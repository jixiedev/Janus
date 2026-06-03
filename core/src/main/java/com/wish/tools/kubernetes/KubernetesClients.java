package com.wish.tools.kubernetes;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class KubernetesClients {

    private KubernetesClients() {}

    static ApiClient create(KubernetesConnectionConfig config) throws IOException {
        Path path = Path.of(config.kubeconfigPath());
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            KubeConfig kubeConfig = KubeConfig.loadKubeConfig(reader);
            config.context().filter(c -> !c.isBlank()).ifPresent(kubeConfig::setContext);
            return ClientBuilder.kubeconfig(kubeConfig).build();
        }
    }
}
