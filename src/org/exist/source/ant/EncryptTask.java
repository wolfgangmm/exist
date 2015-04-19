package org.exist.source.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.exist.source.SourceEncryption;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class EncryptTask extends Task {

    private List<FileSet> fileSetList = null;
    private Path output;
    private String secret;

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setOutput(String output) {
        this.output = Paths.get(output);
    }

    public void addFileset(FileSet set) {
        if (fileSetList == null) {
            fileSetList = new ArrayList<>();
        }
        fileSetList.add(set);
    }

    @Override
    public void execute() throws BuildException {
        if (secret == null) {
            throw new BuildException("You must provide a secret for xmldb:encrypt");
        }
        if (output == null || !output.toFile().canWrite()) {
            throw new BuildException("Output directory not specified or not writable");
        }

        if (fileSetList != null) {
            try {
                Files.createDirectories(output);

                Cipher cipher = Cipher.getInstance("DES");

                DESKeySpec keySpec = new DESKeySpec(secret.getBytes("UTF-8"));
                SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
                SecretKey key = keyFactory.generateSecret(keySpec);

                fileSetList.stream().forEach(fileSet -> {
                    final DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
                    scanner.scan();
                    final String[] includedFiles = scanner.getIncludedFiles();
                    final File baseDir = scanner.getBasedir();

                    Arrays.stream(includedFiles).forEach(file -> {
                        try {
                            encryptFile(cipher, key, baseDir.toPath().resolve(file), output);
                        } catch (InvalidKeyException | IOException e) {
                            log(e.getMessage(), Project.MSG_ERR);
                        }
                    });

                });
            } catch (IOException | GeneralSecurityException e) {
                throw new BuildException("Error while encrypting files: " + e.getMessage(), e);
            }
        }
    }

    private void encryptFile(Cipher cipher, SecretKey key, Path file, Path output) throws InvalidKeyException, IOException {
        String name = file.getFileName().toString();
        Path target = output.resolve(name);

        cipher.init(Cipher.ENCRYPT_MODE, key);

        log("Encrypting " + file + " to " + target);
        try (FileOutputStream out = new FileOutputStream(target.toFile())) {
            out.write("DES".getBytes("UTF-8"));
            try (CipherOutputStream cout = new CipherOutputStream(out, cipher)) {
                Files.copy(file, cout);
            }
        }
    }
}
