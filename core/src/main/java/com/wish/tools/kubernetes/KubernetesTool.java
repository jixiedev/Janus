package com.wish.tools.kubernetes;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.EventsV1Api;
import io.kubernetes.client.openapi.models.EventsV1Event;
import io.kubernetes.client.openapi.models.EventsV1EventList;
import io.kubernetes.client.openapi.models.EventsV1EventSeries;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Objects;

/**
 * Read-only Kubernetes pod operations. Cluster access via kubeconfig ({@link KubeEnvLoader}).
 */
@Slf4j
public class KubernetesTool {

    public static final String LIST_PODS_TOOL = "k8s_list_pods";
    public static final String POD_LOGS_TOOL = "k8s_get_pod_logs";
    public static final String DESCRIBE_POD_TOOL = "k8s_describe_pod";

    private static final int DEFAULT_MAX_PODS = 100;
    private static final int MAX_ALLOWED_PODS = 500;
    private static final int DEFAULT_TAIL_LINES = 200;
    private static final int MAX_ALLOWED_TAIL_LINES = 2000;
    private static final int MAX_LOG_CHARS = 262_144;

    @Tool(
            name = LIST_PODS_TOOL,
            description =
                    "List pods in a Kubernetes namespace. Uses kubeconfig from .kube_env, KUBECONFIG, or ~/.kube/config.")
    public String listPods(
            @ToolParam(description = "Namespace (optional; uses .kube_env default or 'default')", required = false)
                    String namespace,
            @ToolParam(description = "Label selector, e.g. app=airflow (optional)", required = false)
                    String labelSelector,
            @ToolParam(description = "Maximum pods to return (default 100, max 500)", required = false)
                    Integer maxPods) {
        int limit = normalizeMaxPods(maxPods);
        try {
            KubernetesConnectionConfig config = KubeEnvLoader.load();
            String ns = resolveNamespace(namespace, config);
            ApiClient client = KubernetesClients.create(config);
            CoreV1Api api = new CoreV1Api(client);
            var request = api.listNamespacedPod(ns).limit((long) limit);
            if (labelSelector != null && !labelSelector.isBlank()) {
                request.labelSelector(labelSelector.trim());
            }
            V1PodList list = request.execute();
            return formatResult(formatPodList(ns, config, list, limit), true);
        } catch (IllegalStateException e) {
            return formatResult(e.getMessage(), false);
        } catch (ApiException e) {
            log.warn("k8s_list_pods failed: {}", e.getMessage());
            return formatResult("Kubernetes list pods failed: " + apiError(e), false);
        } catch (Exception e) {
            log.warn("k8s_list_pods failed: {}", e.getMessage());
            return formatResult("Kubernetes list pods failed: " + e.getMessage(), false);
        }
    }

    @Tool(
            name = POD_LOGS_TOOL,
            description = "Fetch container logs for a pod. Specify container name when the pod has multiple containers.")
    public String getPodLogs(
            @ToolParam(description = "Pod name", required = true) String podName,
            @ToolParam(description = "Namespace (optional)", required = false) String namespace,
            @ToolParam(description = "Container name (optional; required for multi-container pods)", required = false)
                    String container,
            @ToolParam(description = "Tail lines to return (default 200, max 2000)", required = false)
                    Integer tailLines,
            @ToolParam(description = "Include log timestamps (default false)", required = false)
                    Boolean timestamps) {
        int tail = normalizeTailLines(tailLines);
        try {
            KubernetesConnectionConfig config = KubeEnvLoader.load();
            String ns = resolveNamespace(namespace, config);
            ApiClient client = KubernetesClients.create(config);
            CoreV1Api api = new CoreV1Api(client);
            var request = api.readNamespacedPodLog(podName.trim(), ns).tailLines(tail);
            if (container != null && !container.isBlank()) {
                request.container(container.trim());
            }
            if (Boolean.TRUE.equals(timestamps)) {
                request.timestamps(true);
            }
            String logs = request.execute();
            if (logs != null && logs.length() > MAX_LOG_CHARS) {
                logs = logs.substring(logs.length() - MAX_LOG_CHARS);
                logs = "[truncated to last " + MAX_LOG_CHARS + " chars]\n" + logs;
            }
            return formatResult(
                    "namespace=%s%npod=%s%ntail_lines=%d%nlogs:%n%s"
                            .formatted(ns, podName, tail, logs == null ? "" : logs),
                    true);
        } catch (IllegalStateException e) {
            return formatResult(e.getMessage(), false);
        } catch (ApiException e) {
            log.warn("k8s_get_pod_logs failed: {}", e.getMessage());
            return formatResult("Kubernetes get logs failed: " + apiError(e), false);
        } catch (Exception e) {
            log.warn("k8s_get_pod_logs failed: {}", e.getMessage());
            return formatResult("Kubernetes get logs failed: " + e.getMessage(), false);
        }
    }

    @Tool(
            name = DESCRIBE_POD_TOOL,
            description =
                    "Describe a pod: status, containers, conditions, and recent events (similar to kubectl describe pod).")
    public String describePod(
            @ToolParam(description = "Pod name", required = true) String podName,
            @ToolParam(description = "Namespace (optional)", required = false) String namespace) {
        try {
            KubernetesConnectionConfig config = KubeEnvLoader.load();
            String ns = resolveNamespace(namespace, config);
            ApiClient client = KubernetesClients.create(config);
            CoreV1Api api = new CoreV1Api(client);
            V1Pod pod = api.readNamespacedPod(podName.trim(), ns).execute();
            EventsV1Api eventsApi = new EventsV1Api(client);
            EventsV1EventList events =
                    eventsApi.listNamespacedEvent(ns)
                            .fieldSelector("regarding.name=" + podName.trim())
                            .limit(20L)
                            .execute();
            return formatResult(formatDescribe(ns, config, pod, events), true);
        } catch (IllegalStateException e) {
            return formatResult(e.getMessage(), false);
        } catch (ApiException e) {
            log.warn("k8s_describe_pod failed: {}", e.getMessage());
            return formatResult("Kubernetes describe pod failed: " + apiError(e), false);
        } catch (Exception e) {
            log.warn("k8s_describe_pod failed: {}", e.getMessage());
            return formatResult("Kubernetes describe pod failed: " + e.getMessage(), false);
        }
    }

    private static String formatPodList(String namespace, KubernetesConnectionConfig config, V1PodList list, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("namespace=").append(namespace).append('\n');
        sb.append("kubeconfig=").append(config.kubeconfigPath()).append('\n');
        config.context().ifPresent(c -> sb.append("context=").append(c).append('\n'));
        List<V1Pod> items = list.getItems() == null ? List.of() : list.getItems();
        int count = 0;
        for (V1Pod pod : items) {
            if (count >= limit) {
                break;
            }
            count++;
            V1ObjectMeta meta = pod.getMetadata();
            V1PodSpec spec = pod.getSpec();
            V1PodStatus status = pod.getStatus();
            sb.append("pod ")
                    .append(count)
                    .append(": name=")
                    .append(meta == null ? "" : nullToEmpty(meta.getName()))
                    .append(" phase=")
                    .append(status == null ? "" : nullToEmpty(status.getPhase()))
                    .append(" node=")
                    .append(spec == null ? "" : nullToEmpty(spec.getNodeName()))
                    .append(" started=")
                    .append(status == null ? "" : nullToEmpty(status.getStartTime()))
                    .append('\n');
        }
        sb.append("count=").append(count);
        return sb.toString().trim();
    }

    private static String formatDescribe(
            String namespace, KubernetesConnectionConfig config, V1Pod pod, EventsV1EventList events) {
        StringBuilder sb = new StringBuilder();
        sb.append("namespace=").append(namespace).append('\n');
        sb.append("kubeconfig=").append(config.kubeconfigPath()).append('\n');
        config.context().ifPresent(c -> sb.append("context=").append(c).append('\n'));

        V1ObjectMeta meta = pod.getMetadata();
        V1PodSpec spec = pod.getSpec();
        V1PodStatus status = pod.getStatus();

        sb.append("name=").append(meta == null ? "" : nullToEmpty(meta.getName())).append('\n');
        if (meta != null) {
            sb.append("uid=").append(nullToEmpty(meta.getUid())).append('\n');
            sb.append("labels=").append(meta.getLabels() == null ? "{}" : meta.getLabels()).append('\n');
            sb.append("created=").append(meta.getCreationTimestamp()).append('\n');
        }
        if (spec != null) {
            sb.append("node=").append(nullToEmpty(spec.getNodeName())).append('\n');
            sb.append("service_account=").append(nullToEmpty(spec.getServiceAccountName())).append('\n');
            sb.append("restart_policy=").append(nullToEmpty(spec.getRestartPolicy())).append('\n');
        }
        if (status != null) {
            sb.append("phase=").append(nullToEmpty(status.getPhase())).append('\n');
            sb.append("pod_ip=").append(nullToEmpty(status.getPodIP())).append('\n');
            sb.append("host_ip=").append(nullToEmpty(status.getHostIP())).append('\n');
            sb.append("start_time=").append(status.getStartTime()).append('\n');
            sb.append("qos_class=").append(nullToEmpty(status.getQosClass())).append('\n');
            if (status.getConditions() != null) {
                for (V1PodCondition condition : status.getConditions()) {
                    if (condition == null) {
                        continue;
                    }
                    sb.append("condition ")
                            .append(nullToEmpty(condition.getType()))
                            .append('=')
                            .append(nullToEmpty(condition.getStatus()))
                            .append(" reason=")
                            .append(nullToEmpty(condition.getReason()))
                            .append(" message=")
                            .append(nullToEmpty(condition.getMessage()))
                            .append('\n');
                }
            }
            if (status.getContainerStatuses() != null) {
                for (V1ContainerStatus cs : status.getContainerStatuses()) {
                    appendContainerStatus(sb, cs);
                }
            }
        }
        if (spec != null && spec.getContainers() != null) {
            sb.append("containers_spec:\n");
            for (V1Container container : spec.getContainers()) {
                if (container == null) {
                    continue;
                }
                sb.append("  - name=")
                        .append(nullToEmpty(container.getName()))
                        .append(" image=")
                        .append(nullToEmpty(container.getImage()))
                        .append('\n');
            }
        }
        sb.append("events:\n");
        List<EventsV1Event> eventItems = events == null || events.getItems() == null ? List.of() : events.getItems();
        if (eventItems.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (EventsV1Event event : eventItems) {
                if (event == null) {
                    continue;
                }
                EventsV1EventSeries series = event.getSeries();
                sb.append("  - type=")
                        .append(nullToEmpty(event.getType()))
                        .append(" reason=")
                        .append(nullToEmpty(event.getReason()))
                        .append(" note=")
                        .append(nullToEmpty(event.getNote()))
                        .append(" count=")
                        .append(series == null ? "" : series.getCount())
                        .append(" time=")
                        .append(event.getEventTime())
                        .append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static void appendContainerStatus(StringBuilder sb, V1ContainerStatus cs) {
        sb.append("container ")
                .append(nullToEmpty(cs.getName()))
                .append(" ready=")
                .append(cs.getReady())
                .append(" restart_count=")
                .append(cs.getRestartCount())
                .append(" image=")
                .append(nullToEmpty(cs.getImage()))
                .append('\n');
        V1ContainerState state = cs.getState();
        if (state != null) {
            if (state.getRunning() != null) {
                sb.append("  state=running since=").append(state.getRunning().getStartedAt()).append('\n');
            } else if (state.getWaiting() != null) {
                sb.append("  state=waiting reason=")
                        .append(nullToEmpty(state.getWaiting().getReason()))
                        .append(" message=")
                        .append(nullToEmpty(state.getWaiting().getMessage()))
                        .append('\n');
            } else if (state.getTerminated() != null) {
                sb.append("  state=terminated reason=")
                        .append(nullToEmpty(state.getTerminated().getReason()))
                        .append(" exit_code=")
                        .append(state.getTerminated().getExitCode())
                        .append('\n');
            }
        }
    }

    private static String resolveNamespace(String requestNamespace, KubernetesConnectionConfig config) {
        if (requestNamespace != null && !requestNamespace.isBlank()) {
            return requestNamespace.trim();
        }
        return config.namespace().filter(n -> !n.isBlank()).orElse("default");
    }

    private static String apiError(ApiException e) {
        int code = e.getCode();
        String body = e.getResponseBody();
        if (body != null && !body.isBlank()) {
            return "HTTP " + code + " " + body;
        }
        return Objects.toString(e.getMessage(), "HTTP " + code);
    }

    private static int normalizeMaxPods(Integer maxPods) {
        if (maxPods == null || maxPods <= 0) {
            return DEFAULT_MAX_PODS;
        }
        return Math.min(maxPods, MAX_ALLOWED_PODS);
    }

    private static int normalizeTailLines(Integer tailLines) {
        if (tailLines == null || tailLines <= 0) {
            return DEFAULT_TAIL_LINES;
        }
        return Math.min(tailLines, MAX_ALLOWED_TAIL_LINES);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatResult(String observation, boolean success) {
        return "{observation=%s, success=%s}".formatted(observation, success);
    }
}
