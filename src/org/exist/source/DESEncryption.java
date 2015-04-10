package org.exist.source;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.Base64Encoder;

import javax.crypto.*;
import javax.crypto.spec.DESKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.function.Consumer;

public class DESEncryption {

    private static DESEncryption instance = null;

    public final static DESEncryption getInstance(String key) throws GeneralSecurityException, UnsupportedEncodingException {
        if (instance != null)
            return instance;
        instance = new DESEncryption(key);
        return instance;
    }

    public final static DESEncryption getInstance() throws GeneralSecurityException, IOException {
        if (instance != null)
            return instance;
        instance = new DESEncryption();
        return instance;
    }

    private final static Logger logger = LogManager.getLogger(DESEncryption.class);

    private final Cipher cipher;
    private SecretKey key;

    public DESEncryption(String passwd) throws GeneralSecurityException, UnsupportedEncodingException {
        cipher = Cipher.getInstance("DES");
        DESKeySpec keySpec = new DESKeySpec(passwd.getBytes("UTF-8"));
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        key = keyFactory.generateSecret(keySpec);
    }

    public DESEncryption() throws GeneralSecurityException, IOException {
        cipher = Cipher.getInstance("DES");
        loadKey("http://localhost:8080/exist/apps/key-server/get-key.xql");
    }

    public byte[] decrypt(byte[] data) throws InvalidKeyException, IOException {
        cipher.init(Cipher.DECRYPT_MODE, key);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (CipherOutputStream cos = new CipherOutputStream(bos, cipher)) {
            cos.write(data, 3, data.length - 3);
        }
        return bos.toByteArray();
    }

    public void encryptFiles(Path directory, Path output, String glob, Consumer<String> progressCallback) throws IOException, InvalidKeyException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {
            for (Path path: stream) {
                encryptFile(path, output, progressCallback);
            }
        }
    }

    public void encryptFile(Path file, Path output, Consumer<String> progressCallback) throws InvalidKeyException, IOException {
        String name = file.getFileName().toString();
        Path target = output.resolve(name);

        cipher.init(Cipher.ENCRYPT_MODE, key);

        progressCallback.accept("Encrypting " + file + " to " + target);
        try (FileOutputStream out = new FileOutputStream(target.toFile())) {
            out.write("DES".getBytes("UTF-8"));
            try (CipherOutputStream cout = new CipherOutputStream(out, cipher)) {
                Files.copy(file, cout);
            }
        }
    }

    private void loadKey(String keyServer) throws IOException {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            SecureRandom random = new SecureRandom();
            generator.initialize(1024, random);

            KeyPair pair = generator.generateKeyPair();

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKeySpec pubSpec = keyFactory.getKeySpec(pair.getPublic(), RSAPublicKeySpec.class);

            String pubKey = keyToString(pubSpec.getModulus(), pubSpec.getPublicExponent());

            HttpClient client = new HttpClient();
            PostMethod method = new PostMethod(keyServer);
            method.setRequestBody(new NameValuePair[]{
                    new NameValuePair("key", pubKey)
            });
            int response = client.executeMethod(method);
            if (response != 200) {
                throw new IOException("Failed to connect to key server: " + response);
            }

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, pair.getPrivate());

            CipherInputStream is = new CipherInputStream(method.getResponseBodyAsStream(), cipher);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int read;
            byte[] buf = new byte[128];
            while ((read = is.read(buf)) != -1) {
                bos.write(buf, 0, read);
            }

            byte[] secret = bos.toByteArray();
            System.out.println("Secret: " + new String(secret));
            DESKeySpec keySpec = new DESKeySpec(secret);
            SecretKeyFactory desKeyFactory = SecretKeyFactory.getInstance("DES");
            key = desKeyFactory.generateSecret(keySpec);

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | HttpException | NoSuchPaddingException | InvalidKeyException e) {
            logger.warn(e.getMessage(), e);
            throw new IOException(e.getMessage(), e);
        }
    }

    private static String keyToString(BigInteger mod, BigInteger exp) throws UnsupportedEncodingException {
        Base64.Encoder encoder = Base64.getEncoder();
        String keyStr = String.valueOf(mod) + ":" + String.valueOf(exp);
        System.out.println(keyStr);
        return encoder.encodeToString(keyStr.getBytes("UTF-8"));
    }

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        Path dir = Paths.get(args[0]);
        Path output = Paths.get(args[1]);
        Files.createDirectories(output);
        DESEncryption encryption = new DESEncryption("My secret");
        encryption.encryptFiles(dir, output, "*.{xql,xqm}", message -> System.out.println(message));
    }
}
