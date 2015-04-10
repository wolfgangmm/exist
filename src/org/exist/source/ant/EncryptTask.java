package org.exist.source.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.exist.source.DESEncryption;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                DESEncryption encryption = new DESEncryption(secret);

                fileSetList.stream().forEach(fileSet -> {
                    final DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
                    scanner.scan();
                    final String[] includedFiles = scanner.getIncludedFiles();
                    final File baseDir = scanner.getBasedir();

                    Arrays.stream(includedFiles).forEach(file -> {
                        try {
                            encryption.encryptFile(baseDir.toPath().resolve(file), output, this::log);
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
}
