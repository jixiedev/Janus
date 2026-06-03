package com.wish.tools.kubernetes;

import java.util.Optional;

/** Resolved Kubernetes client settings from kubeconfig and optional overrides. */
public record KubernetesConnectionConfig(
        String kubeconfigPath,
        Optional<String> context,
        Optional<String> namespace,
        String sourceLabel) {}
