package org.exist.source;

import org.apache.commons.httpclient.HttpException;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.ConfigurationHelper;

import javax.crypto.*;
import javax.crypto.spec.DESKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class SourceEncryption {

    public static final String KEY_SERVER = "https://localhost:8443/exist/apps/license-server/get";

    private static SourceEncryption instance = null;

    public final static SourceEncryption getInstance(String key) throws GeneralSecurityException, UnsupportedEncodingException {
        if (instance != null)
            return instance;
        instance = new SourceEncryption(key);
        return instance;
    }

    public final static SourceEncryption getInstance() throws GeneralSecurityException, IOException {
        if (instance != null)
            return instance;
        instance = new SourceEncryption();
        return instance;
    }

    private final static Logger logger = LogManager.getLogger(SourceEncryption.class);

    private final Cipher cipher;
    private SecretKey key;

    public SourceEncryption(String passwd) throws GeneralSecurityException, UnsupportedEncodingException {
        cipher = Cipher.getInstance("DES");

        DESKeySpec keySpec = new DESKeySpec(passwd.getBytes("UTF-8"));
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        key = keyFactory.generateSecret(keySpec);
    }

    public SourceEncryption() throws GeneralSecurityException, IOException {
        cipher = Cipher.getInstance("DES");
        byte[] keyData = loadKey(KEY_SERVER);
        DESKeySpec keySpec = new DESKeySpec(keyData);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        key = keyFactory.generateSecret(keySpec);
    }

    public byte[] decrypt(byte[] data) throws InvalidKeyException, IOException {
        cipher.init(Cipher.DECRYPT_MODE, key);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (CipherOutputStream cos = new CipherOutputStream(bos, cipher)) {
            cos.write(data, 3, data.length - 3);
        }
        return bos.toByteArray();
    }

    private static byte[] loadKey(String keyServer) throws IOException {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            final SecureRandom random = new SecureRandom();
            generator.initialize(1024, random);

            final KeyPair pair = generator.generateKeyPair();

            final X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(pair.getPublic().getEncoded());
            final Base64.Encoder base64Enc = Base64.getEncoder();
            final String pubKey = base64Enc.encodeToString(publicKeySpec.getEncoded());

            CloseableHttpClient client = getHttpClient();

            HttpPost method = new HttpPost(keyServer);
            List<NameValuePair> formparams = new ArrayList<NameValuePair>();
            formparams.add(new BasicNameValuePair("key", pubKey));
            formparams.add(new BasicNameValuePair("license", getLicense()));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, Consts.UTF_8);
            method.setEntity(entity);

            CloseableHttpResponse response = client.execute(method);
            try {
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new IOException("Failed to connect to key server: " + response.getStatusLine().getReasonPhrase());
                }

                final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.DECRYPT_MODE, pair.getPrivate());

                final CipherInputStream is = new CipherInputStream(response.getEntity().getContent(), cipher);
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int read;
                byte[] buf = new byte[128];
                while ((read = is.read(buf)) != -1) {
                    bos.write(buf, 0, read);
                }

                byte[] keyData = bos.toByteArray();
                return keyData;
            } finally {
                response.close();
            }
        } catch (NoSuchAlgorithmException | HttpException | InvalidKeyException | NoSuchPaddingException | KeyStoreException | KeyManagementException e) {
            logger.warn(e.getMessage(), e);
            throw new IOException(e.getMessage(), e);
        }
    }

    private static CloseableHttpClient getHttpClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustStrategy() {
            public boolean isTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                return true;
            }
        });
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(),
                SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        return HttpClients.custom().setSSLSocketFactory(sslsf).build();
    }

    private static String getLicense() throws IOException {
        File home = ConfigurationHelper.getExistHome();
        Path license = home.toPath().resolve("license");
        return Files.lines(license).findFirst().get();
    }
}
