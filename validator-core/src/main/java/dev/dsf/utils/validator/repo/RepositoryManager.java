package dev.dsf.utils.validator.repo;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;

/**
 * Responsible for cloning or updating the remote repository.
 */
public class RepositoryManager {

    /**
     * Clones the remote repository if not already present.
     * If the target directory exists and is non-empty, it is assumed to be already cloned.
     *
     * @param remoteRepoUrl the URL of the remote repository.
     * @param cloneDir      the local directory where the repository should be cloned.
     * @return the File representing the cloned repository directory.
     * @throws GitAPIException if cloning fails.
     */
    public File getRepository(String remoteRepoUrl, File cloneDir) throws GitAPIException {
        if (!cloneDir.exists() || cloneDir.list().length == 0) {
            System.out.println("🔍 Cloning repository from: " + remoteRepoUrl);
            Git.cloneRepository()
                    .setURI(remoteRepoUrl)
                    .setDirectory(cloneDir)
                    .call();
            System.out.println("✅ Repository cloned to: " + cloneDir.getAbsolutePath());
        } else {
            System.out.println("🔍 Repository already cloned at: " + cloneDir.getAbsolutePath());
        }
        return cloneDir;
    }
}
