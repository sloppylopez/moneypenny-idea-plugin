package com.github.sloppylopez.moneypennyideaplugin.services

import com.github.sloppylopez.moneypennyideaplugin.Bundle
import com.github.sloppylopez.moneypennyideaplugin.global.GlobalData.downerTabName
import com.github.sloppylopez.moneypennyideaplugin.global.GlobalData.prompts
import com.github.sloppylopez.moneypennyideaplugin.global.GlobalData.tabNameToContentPromptTextMap
import com.github.sloppylopez.moneypennyideaplugin.global.GlobalData.tabNameToFilePathMap
import com.github.sloppylopez.moneypennyideaplugin.helper.ToolWindowHelper.Companion.addTabbedPaneToToolWindow
import com.google.gson.Gson
import com.intellij.icons.AllIcons
import com.intellij.notification.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.Content
import java.awt.Container
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.*
import javax.swing.*
import kotlin.collections.ArrayList

@Service(Service.Level.PROJECT)
class ProjectService(project: Project? = ProjectManager.getInstance().openProjects[0]) {
    private val CURRENT_PROCESS_PROMPT = Key.create<String>("Current Processed Prompt")
    private val gitService = project?.service<GitService>()
    private val pluginId = "MoneyPenny AI"

    fun getFileContents(filePath: String?) = filePath?.let {
        try {
            File(it).readText()
        } catch (e: Exception) {
            thisLogger().error(e.stackTraceToString())
        }
    } as String

    fun getRandomNumber() = (1..100).random()

    fun fileToVirtualFile(file: File?): VirtualFile? {
        val localFileSystem = LocalFileSystem.getInstance()
        return file?.let { localFileSystem.findFileByIoFile(it) }
    }

    fun virtualFileToFile(virtualFile: VirtualFile?): File? {
        return virtualFile?.let { File(it.path) }
    }

    fun psiFileToFile(file: PsiFile?): File? {
        return file?.virtualFile?.let { virtualFile ->
            File(virtualFile.path)
        }
    }

    fun getText(file: File?, message: String?): String {
        if (file != null && message == null) {
            val reader = BufferedReader(FileReader(file))
            reader.use {
                val contents = StringBuilder()
                var line: String? = reader.readLine()
                while (line != null) {
                    contents.append(line).append(System.lineSeparator())
                    line = reader.readLine()
                }
                return contents.toString()
            }
        } else if (message != null) {
            return message
        }
        return ""
    }

    fun showDialog(
        message: String,
        title: String,
        buttons: Array<String>? = emptyArray<String>(),
        defaultOptionIndex: Int? = 0,
        icon: Icon? = AllIcons.Icons.Ide.NextStep
    ) {
        Messages.showDialog(
            message,
            title,
            buttons!!,
            defaultOptionIndex!!,
            icon
        )
    }

    fun readFile(fileList: List<*>, i: Int): File? {
        try {
            if (i < fileList.size && fileList.isNotEmpty() &&
                null != fileList[i]
            ) {
                val file = fileList[i] as File
                thisLogger().info(Bundle.message("projectService", "File $file"))
                return file
            } else {
                thisLogger().info(Bundle.message("projectService", "File is null"))
            }
        } catch (e: Exception) {
            thisLogger().error(Bundle.message("projectService", e))
        }
        return null
    }

    fun showNotification(
        @NlsSafe title: String,
        @NlsSafe message: String
    ) {
        val notification = Notification(
            "MoneyPenny",
            title,
            message,
            NotificationType.INFORMATION
        )

        Notifications.Bus.notify(notification, this.getProject()!!)
    }

    fun showMessage(
        message: String, title: String
    ) {
        Messages.showInfoMessage(
            message, title,
        )
    }

    fun highlightTextInEditor(contentPromptText: String) {
        val editor = getCurrentEditor()
        editor?.let {
            val document = editor.document
            val textOffset = document.text.indexOf(contentPromptText)
            if (textOffset != -1) {
                editor.caretModel.moveToOffset(textOffset)
                editor.selectionModel.setSelection(textOffset, textOffset + contentPromptText.length)
            }
        }
    }

    private fun getCurrentEditor(): Editor? {
        val project = this.getProject()!!
        val file = FileEditorManager.getInstance(project)?.selectedFiles?.firstOrNull()
        return file?.let { FileEditorManager.getInstance(project).selectedTextEditor }
    }

    fun expandFolders(fileList: List<*>? = null): List<File> {
        if (fileList == null) {
            return emptyList()
        }
        val expandedFileList = mutableListOf<File>()
        val stack = Stack<Any>()
        stack.addAll(fileList)

        while (stack.isNotEmpty()) {
            expand(stack, expandedFileList)
        }

        return expandedFileList
    }

    private fun expand(
        stack: Stack<Any>,
        expandedFileList: MutableList<File>
    ) {
        val file = stack.pop()
        try {
            when (file) {
                is File -> {
                    if (file.isDirectory) {
                        val files = file.listFiles()
                        if (files != null) {
                            stack.addAll(files.toList())
                        }
                    } else {
                        expandedFileList.add(file)
                    }
                }

                is VirtualFile -> {
                    if (file.isDirectory) {
                        val children = file.children.toList()
                        stack.addAll(children)
                    } else {
                        expandedFileList.add(virtualFileToFile(file)!!)
                    }
                }
            }
        } catch (e: Exception) {
            thisLogger().error(e)
        }
    }

    private fun getIsSnippet(normalizedFileContent: String?, normalizedSelectedText: String?) =
        normalizedFileContent != null && normalizedSelectedText?.trim() != normalizedFileContent.trim()

    private fun getSelectedTextFromOpenedFileInEditor(
        selectedEditor: Editor
    ): @NlsSafe String? {
        var selectedText: @NlsSafe String? = ""
        val project: Project? = selectedEditor.project
        val fileEditorManager = FileEditorManager.getInstance(project!!)
        val selectedFile = fileEditorManager.selectedFiles.firstOrNull()
        if (selectedFile != null) {
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl(selectedFile.url)
            val openFileDescriptor = OpenFileDescriptor(project, virtualFile!!)
            val document = openFileDescriptor.file.let { FileDocumentManager.getInstance().getDocument(it) }
            selectedText = document?.text
        }
        return selectedText
    }

    fun setTabName(
        i: Int,
        fileList: List<*>,
        file: File?,
        tabbedPane: JBTabbedPane,
        panel: JPanel,
        contentPromptText: String?
    ) {
        if (i < fileList.size && file != null) {
            val tabName = "${getNextTabName()}) ${file.name}"
            tabbedPane.addTab(tabName, panel)
            tabNameToFilePathMap[tabName] = file.canonicalPath
            if (contentPromptText != null) {
                tabNameToContentPromptTextMap[tabName] = contentPromptText
            } else {
                tabNameToContentPromptTextMap[tabName] = file.readText()
            }
        } else {
            tabbedPane.addTab("No File", panel)
        }

        if (contentPromptText != null && file != null) {
            val tabName = "$downerTabName) ${file.name}"
            tabNameToContentPromptTextMap[tabName] = contentPromptText
        }
    }

    private fun getNextTabName(): String {
        return downerTabName++.toString()
    }

    fun getProject(): Project? {
        return ProjectManager.getInstance().openProjects.firstOrNull()
    }

    fun getToolWindow(): ToolWindow? {
        return ToolWindowManager.getInstance(getProject()!!).getToolWindow(pluginId)
    }

    fun sendFileToContentPrompt(
        editor: Editor?,
        file: File?,
    ) {
        try {
            editor?.let { selectedEditor ->
                var selectedTextFromEditor = selectedEditor.selectionModel.selectedText
                if (selectedTextFromEditor.isNullOrEmpty()) {
                    selectedTextFromEditor = this.getSelectedTextFromOpenedFileInEditor(selectedEditor)
                }
                if (!selectedTextFromEditor.isNullOrEmpty()) {
                    addTabbedPaneToToolWindow(
                        this.getProject()!!,
                        listOf(file),
                        selectedTextFromEditor
                    )
                }
            }
        } catch (e: Exception) {
            thisLogger().error(e.stackTraceToString())
        }
    }

    fun invokeLater(functionsToRunLater: () -> Unit) {
        SwingUtilities.invokeLater {
            ApplicationManager.getApplication().invokeLater(
                functionsToRunLater,
                ModalityState.NON_MODAL
            )
        }
    }

    fun isSnippet(contentPromptText: String?, virtualFile: VirtualFile?): Boolean {
        val normalizedSelectedText = contentPromptText?.replace("\r\n", "\n")
        val normalizedFileContent =
            virtualFile?.contentsToByteArray()?.toString(Charsets.UTF_8)?.replace("\r\n", "\n")
        return this.getIsSnippet(normalizedFileContent, normalizedSelectedText)
    }

    fun copyToClipboard(text: String? = null) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = StringSelection(text)
        clipboard.setContents(stringSelection, null)
    }

    fun getSelectedTabText(tabbedPane: JTabbedPane): String? {
        val selectedTabIndex = tabbedPane.selectedIndex
        if (selectedTabIndex != -1) {
            val selectedTabTitle = tabbedPane.getTitleAt(selectedTabIndex)
            if (!selectedTabTitle.isNullOrEmpty()) {
                return tabNameToContentPromptTextMap[selectedTabTitle]?: ""
            }
        }
        return null
    }

    fun putUserDataInProject(fileList: List<*>) {
        this.getProject()?.putUserData(Key.create("All Processed Prompt"), fileList)
    }

    fun getUserDataFromProject(key: Key<List<*>>) {
        this.getProject()?.getUserData(key)
    }

    fun putUserDataInComponent(fileList: List<*>, content: Content) {
        val myfiles = ArrayList<String>()
        myfiles.add("hello")
        content.putUserData(CURRENT_PROCESS_PROMPT, myfiles.toString())
    }

    fun getUserDataFromComponent(key: Key<List<*>>, content: Content) {
        content.getUserData(key)
    }

    fun getPrompts(): String {
        prompts.clear()
        val contentManager = getToolWindow()?.contentManager
        val contentCount = contentManager?.contentCount
        for (i in 0 until contentCount!!) {
            val content = contentManager.getContent(i)
            val simpleToolWindowPanel = content?.component as? SimpleToolWindowPanel
            if (simpleToolWindowPanel != null) {
                val textAreas: ArrayList<String> = ArrayList()
                findTextAreas(simpleToolWindowPanel, textAreas)
                val promptsAsJson = getPromptsAsJson(prompts)
                saveDataToExtensionFolder(promptsAsJson)
                return textAreas.joinToString("\n")
            }
        }
        return ""
    }

    private fun findTextAreas(container: Container, textAreas: ArrayList<String>) {
        val components = container.components
        for (component in components) {
            if (component is JTextArea) {
                val text = component.text
                val parentTabbedPane = component.parent.parent.parent.parent.parent
                if (parentTabbedPane is JTabbedPane) {
                    extractPromptInfo(parentTabbedPane, text, textAreas)
                }
            } else if (component is Container) {
                findTextAreas(component, textAreas)
            }
        }
    }

    private fun extractPromptInfo(
        parentTabbedPane: JTabbedPane,
        text: String,
        textAreas: ArrayList<String>
    ) {
        val tabName = parentTabbedPane.getTitleAt(0)
        if (!tabName.isNullOrEmpty()
        ) {
            val shortSha = gitService?.getShortSha(tabNameToFilePathMap[tabName] ?: "")!!
            val promptMap = prompts.getOrDefault(shortSha, mutableMapOf())
            val promptList = promptMap.getOrDefault(tabName, listOf())

            prompts[shortSha] = promptMap + (tabName to promptList.plus(text))
            textAreas.add(text)
        }
    }

    private fun getPromptsAsJson(prompts: MutableMap<String, Map<String, List<String>>>): String {
        return Gson().toJson(prompts)
    }

    private fun saveDataToExtensionFolder(data: String) {
        val extensionFolder = File(PathManager.getPluginsPath(), pluginId)
        if (!extensionFolder.exists()) {
            extensionFolder.mkdir()
        }
        val dataFile = File(extensionFolder, "prompt_history.json")
        dataFile.writeText(data)
    }

    fun loadDataFromExtensionFolder(): String {
        val extensionFolder = File(PathManager.getPluginsPath(), pluginId)
        val dataFile = File(extensionFolder, "prompt_history.txt")
        return dataFile.readText()
    }

    fun escapePrompt(prompt: String): String {
        val escapedPrompt = prompt.replace(Regex("[\n\r\t\"]")) { matchResult ->
            when (matchResult.value) {
                "\n" -> "\\n"
                "\r" -> "\\r"
                "\t" -> "\\t"
                "\"" -> "\\\""
                else -> matchResult.value
            }
        }
        return escapedPrompt
    }

    fun escapePromptLines(prompt: String): String {
        val escapedPrompt = StringBuilder()
        var isInsideQuotes = false
        var isInsideSingleQuotes = false

        for (char in prompt) {
            when {
                char == '\"' && !isInsideSingleQuotes -> {
                    escapedPrompt.append("\\\"")
                    isInsideQuotes = !isInsideQuotes
                }
                char == '\'' && !isInsideQuotes -> {
                    escapedPrompt.append("\\\'")
                    isInsideSingleQuotes = !isInsideSingleQuotes
                }
                isInsideQuotes || isInsideSingleQuotes -> {
                    when (char) {
                        '\n' -> escapedPrompt.append("\\n")
                        '\r' -> escapedPrompt.append("\\r")
                        '\t' -> escapedPrompt.append("\\t")
                        else -> escapedPrompt.append(char)
                    }
                }
                else -> escapedPrompt.append(char)
            }
        }

        return escapedPrompt.toString()
    }



}