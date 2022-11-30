package ru.hixon.openfileinazuredevops.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.net.URI
import java.net.URISyntaxException

// Opens current file in Azure DevOps
// Based on https://github.com/JetBrains/intellij-community/blob/master/plugins/github/src/org/jetbrains/plugins/github/GHOpenInBrowserActionGroup.kt
class OpenFileInAzureDevOpsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val dataContext = e.dataContext
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().runReadAction {
                prepareAndOpenFile(dataContext, project)
            }
        }
    }

    private fun prepareAndOpenFile(
        dataContext: DataContext,
        project: Project
    ) {
        val virtualFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val repository: GitRepository =
            GitRepositoryManager.getInstance(project).getRepositoryForFile(virtualFile) ?: return

        val changeListManager = ChangeListManager.getInstance(project)
        if (changeListManager.isUnversioned(virtualFile)) return

        val change = changeListManager.getChange(virtualFile)
        if (change != null && change.type == Change.Type.NEW) return

        openFileInBrowser(repository.root, repository, virtualFile)
    }

    private fun openFileInBrowser(
        repositoryRoot: VirtualFile,
        path: GitRepository,
        virtualFile: VirtualFile
    ) {
        val relativePath = VfsUtilCore.getRelativePath(virtualFile, repositoryRoot) ?: return

        val remote = path.remotes.firstOrNull() ?: return
        var originUrl = remote.firstUrl ?: return

        if (originUrl.contains('@')) {
            // need to remove login
            originUrl = originUrl.replace(Regex("[a-zA-Z0-9]+@"), "")
        }

        BrowserUtil.browse("$originUrl?path=" + encodePath(relativePath))
    }

    private fun encodePath(path: String?): String? {
        return try {
            URI(null, null, path, null, null).toASCIIString()
        } catch (e: URISyntaxException) {
            path
        }
    }
}