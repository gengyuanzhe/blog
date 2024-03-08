import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;

/**
 * Main
 *
 * @author g00471473
 * @since 2024-01-15
 */
public class Main {

    static class DelayInputStream extends InputStream {

        int count = 0;

        @Override public int read() throws IOException {
            if (count / 2048 == 20) {
                return -1;
            }

            if (count != 0 && count % 10240 == 0) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            int result = 'a' + count / 2048;
            count++;
            return result;
        }
    }

    public static void main(String[] args) throws Exception {
        // 创建 HttpClient 实例
        CloseableHttpClient httpClient = HttpClients.createDefault();

        // 设置上传的目标 URL
        String url = "http://127.0.0.1:1123/async";
//        String url = "http://7.220.59.235:1123/async";

        // 模拟待上传的大量数据
        String largeData = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
            + "Integer euismod, justo non placerat dignissim, enim nunc semper neque, "
            + "non efficitur odio ipsum in ligula. ... (your large data)";

        // 将数据转换为输入流
        InputStream inputStream = new DelayInputStream();
        // 创建流实体
        InputStreamEntity entity = new InputStreamEntity(inputStream, ContentType.DEFAULT_BINARY);

        // 创建 HttpPost 请求
        HttpPut httpPut = new HttpPut(url);

        // 设置请求的实体
        httpPut.setEntity(entity);

        // 执行请求
        CloseableHttpResponse response = httpClient.execute(httpPut);
        System.out.println(response.getCode());
        HttpEntity responseEntity =    response.getEntity();
                // 处理响应

        String responseBody = EntityUtils.toString(responseEntity);
        System.out.println("Response: " + responseBody);

        // 关闭 HttpClient 和输入流
        httpClient.close();
        inputStream.close();
    }
}
