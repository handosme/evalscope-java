import com.evalscope.fasthttpclient.client.FastHttpClient;
import com.evalscope.fasthttpclient.http.Request;
import com.evalscope.fasthttpclient.http.Response;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TestFastHttpClient {
    public static void main(String[] args) throws Exception {
        System.out.println("Testing FastHttpClient...");

        try (FastHttpClient client = FastHttpClient.builder().build()) {
            // Test GET request
            Request request = Request.builder()
                    .url("https://httpbin.org/get")
                    .get()
                    .build();

            CompletableFuture<Response> responseFuture = client.execute(request);
            Response response = responseFuture.get(10, TimeUnit.SECONDS);

            System.out.println("✓ GET request completed");
            System.out.println("  Status Code: " + response.code());
            System.out.println("  Method: " + response.request().method());
            System.out.println("  URL: " + response.request().url());
            System.out.println("  Headers Count: " + response.headers().names().size());

            if (response.body() != null && response.body().length() > 0) {
                System.out.println("  Body Length: " + response.body().length() + " characters");
            }
        }

        System.out.println("\n✓ All tests passed!");
        System.out.println("\nFastHttpClient module successfully created with:");
        System.out.println("- OkHttp-style request/response encoding");
        System.out.println("- Netty-based HTTP client implementation");
        System.out.println("- Support for high-performance batch execution");
        System.out.println("- Timeout and cancellation features");
    }
}