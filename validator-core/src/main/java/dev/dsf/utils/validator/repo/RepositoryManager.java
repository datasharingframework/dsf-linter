package dev.dsf.utils.validator.repo;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.util.Objects;

/**
 * <h2>Repository Manager</h2>
 *
 * <p>
 * The {@code RepositoryManager} class provides a simple mechanism to clone a remote Git repository
 * using the JGit library. It is designed for use in automated validation pipelines or tooling
 * where project resources need to be downloaded dynamically.
 * </p>
 *
 * <h3>Functionality:</h3>
 * <ul>
 *   <li>Checks if a target directory exists and is non-empty.</li>
 *   <li>If the directory is missing or empty, it performs a Git clone.</li>
 *   <li>If the directory exists and is not empty, it assumes the repository is already cloned.</li>
 * </ul>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * RepositoryManager manager = new RepositoryManager();
 * File repo = manager.getRepository("https://git.example.org/project.git", new File("tmp/project"));
 * }</pre>
 *
 * <h3>Dependencies:</h3>
 * <ul>
 *   <li><a href="https://www.eclipse.org/jgit/">JGit</a> – Java library to work with Git repositories</li>
 * </ul>
 */
public class RepositoryManager {
    /**
     * Retrieves a Git repository from the given remote URL.
     *
     * <p>
     * If the target directory does not exist or is empty, the repository is cloned
     * using {@code org.eclipse.jgit.api.Git.cloneRepository()}.
     * If the directory already exists and contains files, it is assumed that the repository
     * is already cloned and no further action is taken.
     * </p>
     *
     * @param remoteRepoUrl the URL of the remote Git repository (e.g., HTTPS or SSH URL)
     * @param cloneDir      the local directory where the repository should be cloned
     * @return a {@link File} representing the directory of the cloned repository
     * @throws GitAPIException if the cloning process fails due to a Git error
     */

    public File getRepository(String remoteRepoUrl, File cloneDir) throws GitAPIException {
        if (!cloneDir.exists() || Objects.requireNonNull(cloneDir.list()).length == 0) {
            System.out.println("Cloning repository from: " + remoteRepoUrl);
            Git.cloneRepository()
                    .setURI(remoteRepoUrl)
                    .setDirectory(cloneDir)
                    .call();
            System.out.println("Repository cloned to: " + cloneDir.getAbsolutePath());
        } else {
            System.out.println("Repository already cloned at: " + cloneDir.getAbsolutePath());
        }
        return cloneDir;
    }
}
