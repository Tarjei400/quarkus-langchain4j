package io.quarkiverse.langchain4j.deployment.devservice;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;

public class OllamaClientTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    private OllamaClient client;

    @BeforeEach
    void setUp() {
        client = OllamaClient.create(new OllamaClient.Options("localhost", getResolvedWiremockPort()));
    }

    @Test
    public void testLocalModels() {
        wiremock().register(
                get(urlEqualTo("/api/tags"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        """
                                                {
                                                  "models": [
                                                    {
                                                      "name": "codellama:13b",
                                                      "modified_at": "2023-11-04T14:56:49.277302595-07:00",
                                                      "size": 7365960935,
                                                      "digest": "9f438cb9cd581fc025612d27f7c1a6669ff83a8bb0ed86c94fcf4c5440555697",
                                                      "details": {
                                                        "format": "gguf",
                                                        "family": "llama",
                                                        "families": null,
                                                        "parameter_size": "13B",
                                                        "quantization_level": "Q4_0"
                                                      }
                                                    },
                                                    {
                                                      "name": "llama3:latest",
                                                      "modified_at": "2023-12-07T09:32:18.757212583-08:00",
                                                      "size": 3825819519,
                                                      "digest": "fe938a131f40e6f6d40083c9f0f430a515233eb2edaa6d72eb85c50d64f2300e",
                                                      "details": {
                                                        "format": "gguf",
                                                        "family": "llama",
                                                        "families": null,
                                                        "parameter_size": "7B",
                                                        "quantization_level": "Q4_0"
                                                      }
                                                    }
                                                  ]
                                                }""")));

        List<OllamaClient.ModelInfo> llama3Response = client.localModels();
        assertThat(llama3Response).hasSize(2).extracting("name").containsExactly("codellama:13b", "llama3:latest");
    }

    @Test
    public void testModelInfo() {
        wiremock().register(
                post(urlEqualTo("/api/show"))
                        .withRequestBody(matchingJsonPath("$.name", equalTo("llama3")))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        """
                                                {
                                                  "modelfile": "# Modelfile generated by \\"ollama show\\"\\n# To build a new Modelfile based on this one, replace the FROM line with:\\n# FROM llava:latest\\n\\nFROM /Users/matt/.ollama/models/blobs/sha256:200765e1283640ffbd013184bf496e261032fa75b99498a9613be4e94d63ad52\\nTEMPLATE \\"\\"\\"{{ .System }}\\nUSER: {{ .Prompt }}\\nASSSISTANT: \\"\\"\\"\\nPARAMETER num_ctx 4096\\nPARAMETER stop \\"\\u003c/s\\u003e\\"\\nPARAMETER stop \\"USER:\\"\\nPARAMETER stop \\"ASSSISTANT:\\"",
                                                  "parameters": "num_ctx                        4096\\nstop                           \\u003c/s\\u003e\\nstop                           USER:\\nstop                           ASSSISTANT:",
                                                  "template": "{{ .System }}\\nUSER: {{ .Prompt }}\\nASSSISTANT: ",
                                                  "details": {
                                                    "format": "gguf",
                                                    "family": "llama",
                                                    "families": ["llama", "clip"],
                                                    "parameter_size": "7B",
                                                    "quantization_level": "Q4_0"
                                                  }
                                                }
                                                """)));

        OllamaClient.ModelInfo llama3Response = client.modelInfo("llama3");
        assertThat(llama3Response).isNotNull().satisfies(ir -> {
            assertThat(ir.modelFile()).isNotBlank();
            assertThat(ir.details()).isNotNull().satisfies(details -> {
                assertThat(details.family()).isEqualTo("llama");
            });
        });

        assertThatThrownBy(() -> client.modelInfo("llama2")).message().contains("llama2").contains("not found");
    }

    @Test
    public void testPullAsync() {
        wiremock().register(
                post(urlEqualTo("/api/pull"))
                        .withRequestBody(matchingJsonPath("$.name", equalTo("llama3")))
                        .willReturn(aResponse()
                                .withBody(
                                        """
                                                {"status": "pulling manifest"}
                                                {"status": "downloading digestname", "digest": "digestname", "total": 2142590208, "completed": 241970}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084,"completed":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084,"completed":1084}
                                                {"status":"pulling fa8235e5b48f","digest":"sha256:fa8235e5b48faca34e3ca98cf4f694ef08bd216d28b58071a1f85b1d50cb814d","total":1084,"completed":1084}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140,"completed":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140,"completed":140}
                                                {"status":"pulling d47ab88b61ba","digest":"sha256:d47ab88b61ba20ed39a1b205a7d5a8e201dcf09107e6b05f128778c32baa4a69","total":140,"completed":140}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485,"completed":485}
                                                {"status":"pulling f7eda1da5a81","digest":"sha256:f7eda1da5a818b34467058265a8d05258bae4b9aa66779753403bd6ea7c91d55","total":485,"completed":485}
                                                {"status":"verifying sha256 digest"}
                                                {"status":"writing manifest"}
                                                {"status":"removing any unused layers"}
                                                {"status":"success"}""")));

        AssertSubscriber<OllamaClient.PullAsyncLine> successfulSubscriber = Multi.createFrom()
                .publisher(client.pullAsync("llama3"))
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        List<OllamaClient.PullAsyncLine> lines = successfulSubscriber.assertCompleted().getItems();
        assertThat(lines).hasSize(54);
        assertThat(lines).first().satisfies(f -> {
            assertThat(f.status()).isEqualTo("pulling manifest");
        });
        assertThat(lines).last().satisfies(l -> {
            assertThat(l.status()).isEqualTo("success");
        });

        wiremock().register(
                post(urlEqualTo("/api/pull"))
                        .withRequestBody(matchingJsonPath("$.name", equalTo("dummy")))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withBody("{\"error\":\"pull model manifest: file does not exist\"}")));

        AssertSubscriber<OllamaClient.PullAsyncLine> failedSubscriber = Multi.createFrom().publisher(client.pullAsync("dummy"))
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        failedSubscriber.assertFailedWith(OllamaClient.ModelDoesNotExistException.class);
    }

    @Test
    @Disabled("This is only meant to be executed by users on demand against a real Ollama server")
    public void testRealPullAsync() {
        BigDecimal ONE_HUNDRED = new BigDecimal("100");

        var client = OllamaClient.create(new OllamaClient.Options("localhost", 11434));
        AssertSubscriber<OllamaClient.PullAsyncLine> failedSubscriber = Multi.createFrom()
                .publisher(client.pullAsync("orca-mini")).onItem().invoke(line -> {
                    if (line.total() != null && line.completed() != null) {
                        BigDecimal percentage = new BigDecimal(line.completed()).divide(new BigDecimal(line.total()), 4,
                                RoundingMode.HALF_DOWN).multiply(ONE_HUNDRED);
                        System.out.printf("Progress: %s%%\n", percentage.setScale(2, RoundingMode.HALF_DOWN));
                    }
                })
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        failedSubscriber.assertCompleted();
    }

}
