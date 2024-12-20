package com.github.sloppylopez.moneypennyideaplugin.services

import com.github.sloppylopez.moneypennyideaplugin.Bundle
import com.github.sloppylopez.moneypennyideaplugin.actions.*
import com.github.sloppylopez.moneypennyideaplugin.components.ChatWindowContent
import com.github.sloppylopez.moneypennyideaplugin.data.GlobalData
import com.github.sloppylopez.moneypennyideaplugin.data.GlobalData.tabNameToContentPromptTextMap
import com.github.sloppylopez.moneypennyideaplugin.data.GlobalData.tabNameToFilePathMap
import com.github.sloppylopez.moneypennyideaplugin.helper.ToolWindowHelper.Companion.addTabbedPaneToToolWindow
import com.google.gson.Gson
import com.intellij.icons.AllIcons
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.Content
import io.ktor.util.*
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.*
import java.util.stream.Collectors
import javax.swing.*


@Service(Service.Level.PROJECT)
class ProjectService {
    private val currentProcessPrompt = Key.create<String>("Current Processed Prompt")
    private val pluginId = "MoneyPenny AI"

    fun getFileContents(filePath: String?) = filePath?.let {
        try {
            File(it).readText()
        } catch (e: Exception) {
            thisLogger().error(e.stackTraceToString())
        }
    } as String

    fun extractCommentsFromCode(input: String): String {
        val regex = Regex("```\\w*\\n(.*?)```", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
        val matchResult = regex.find(input)
        return matchResult?.groups?.get(1)?.value?.trim() ?: input
    }

    fun isCodeCommented(input: String): Boolean {
        val regex = Regex("```\\w*\\n(.*?)```", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
        val matchResult = regex.find(input)
        return matchResult != null
    }

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
        @NlsSafe message: String,
        notificationType: NotificationType?,
        customIcon: Icon? = null,
        imageName: String? = null
    ) {
        val notification = Notification(
            "MoneyPennyAI",
            title,
            message.escapeHTML(),
            notificationType ?: NotificationType.INFORMATION
        )
        notification.setIcon(customIcon ?: createCustomIcon(imageName ?: "/images/moneypenny-ai-notification.png"))
        Notifications.Bus.notify(notification, this.getProject()!!)
    }

    private fun createCustomIcon(imageName: String): Icon {
        // Code to generate a random image and create an icon from it
        return ImageIcon(SendToPromptFileFolderTreeActionParallel::class.java.getResource(imageName))
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
            val normalizedCarryReturnsContentPromptText = contentPromptText.replace("\r\n", "\n")
            val textOffset = document.text.indexOf(normalizedCarryReturnsContentPromptText)
            if (textOffset != -1) {
                editor.caretModel.moveToOffset(textOffset)
                editor.selectionModel.setSelection(
                    textOffset,
                    textOffset + normalizedCarryReturnsContentPromptText.length
                )
            }
        }
    }

    fun highlightTextInAllEditors(contentPromptText: String) {
        val editors = getAllOpenEditors() // New helper function to get all editors
        val normalizedContentPromptText = contentPromptText.replace("\r\n", "\n")

        editors.forEach { editor ->
            val document = editor.document
            val textOffset = document.text.indexOf(normalizedContentPromptText)
            if (textOffset != -1) {
                editor.caretModel.moveToOffset(textOffset)
                editor.selectionModel.setSelection(textOffset, textOffset + normalizedContentPromptText.length)
            }
        }
    }

    // Helper function to get all open editors in the project
    private fun getAllOpenEditors(): List<Editor> {
        val fileEditorManager = FileEditorManager.getInstance(this.getProject()!!)
        return fileEditorManager.allEditors.mapNotNull { editor ->
            (editor as? TextEditor)?.editor
        }
    }

    private fun getCurrentEditor(): Editor? {
        val project = this.getProject()!!
        val file = FileEditorManager.getInstance(project)?.selectedFiles?.firstOrNull()
        return file?.let { FileEditorManager.getInstance(project).selectedTextEditor }
    }

    fun modifySelectedTextInEditorByFile(
        newText: String,
        file: VirtualFile,
    ) {
        try {
            val project = this.getProject() ?: return

            ApplicationManager.getApplication().invokeLater {
                val editorManager = FileEditorManager.getInstance(project)
                val editor = editorManager.openTextEditor(OpenFileDescriptor(project, file), false)
                editor?.let {
                    val document = editor.document
                    val selectionModel = editor.selectionModel
                    val startOffset = selectionModel.selectionStart
                    val endOffset = selectionModel.selectionEnd

                    if (startOffset != endOffset) {
                        ApplicationManager.getApplication().runWriteAction {
                            WriteCommandAction.runWriteCommandAction(project) {
                                document.replaceString(startOffset, endOffset, newText)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            thisLogger().error(Bundle.message("projectService", e))
        }
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
        contentPromptText: String?,
        tabName: String
    ) {
        // Always add the tab
        tabbedPane.addTab(tabName, panel)

        // If we have a file and it's within the fileList bounds, set file-based metadata
        if (i < fileList.size && file != null) {
            tabNameToFilePathMap[tabName] = file.canonicalPath
            if (contentPromptText != null) {
                tabNameToContentPromptTextMap[tabName] = contentPromptText
            } else {
                tabNameToContentPromptTextMap[tabName] = file.readText()
            }
        } else {
            // If we don't have a file (e.g. concat mode), still store the contentPromptText if available
            if (contentPromptText != null) {
                tabNameToContentPromptTextMap[tabName] = contentPromptText
            } else {
                // Optionally, set it to an empty string or skip altogether if not needed.
                tabNameToContentPromptTextMap[tabName] = ""
            }
        }

        // The following block attempts to set content again based on file and contentPromptText
        // but is redundant and can cause confusion, especially in concat mode.
        // Remove or revise it if it's not absolutely necessary.
        //
        // if (contentPromptText != null && file != null) {
        //     val currentTabName = "$downerTabName) ${file.name}"
        //     tabNameToContentPromptTextMap[currentTabName] = contentPromptText
        // }
    }


    fun getProject(): Project? {
        return ProjectManager.getInstance().openProjects.firstOrNull()
    }

    fun getToolWindow(): ToolWindow? {
        return ToolWindowManager.getInstance(getProject()!!).getToolWindow(pluginId)
    }

//    fun sendFileToContentPrompt(
//        editor: Editor?,
//        file: File?,
//    ) {
//        try {
//            editor?.let { selectedEditor ->
//                var selectedTextFromEditor = selectedEditor.selectionModel.selectedText
//                if (selectedTextFromEditor.isNullOrEmpty()) {
//                    selectedTextFromEditor = this.getSelectedTextFromOpenedFileInEditor(selectedEditor)
//                }
//                if (!selectedTextFromEditor.isNullOrEmpty()) {
//                    addTabbedPaneToToolWindow(
//                        this.getProject()!!,
//                        listOf(file),
//                        selectedTextFromEditor
//                    )
//                }
//            }
//        } catch (e: Exception) {
//            thisLogger().error(e.stackTraceToString())
//        }
//    }

    fun getSelectedTextFromEditor(editor: Editor?): String? {
        return editor?.let { selectedEditor ->
            var selectedTextFromEditor = selectedEditor.selectionModel.selectedText
            if (selectedTextFromEditor.isNullOrEmpty()) {
                selectedTextFromEditor = this.getSelectedTextFromOpenedFileInEditor(selectedEditor)
            }
            selectedTextFromEditor
        }
    }

    fun addSelectedTextToTabbedPane(editor: Editor?, file: File?, isConcat: Boolean) {
        try {
            val selectedTextFromEditor = getSelectedTextFromEditor(editor)
            if (!selectedTextFromEditor.isNullOrEmpty()) {
                addTabbedPaneToToolWindow(
                    this.getProject()!!,
                    listOf(file),
                    selectedTextFromEditor,
                    isConcat
                )
            }
        } catch (e: Exception) {
            thisLogger().error(e.stackTraceToString())
        }
    }

    fun invokeLater(functionsToRunLater: () -> Unit) {
        SwingUtilities.invokeLater {
            ApplicationManager.getApplication().invokeLater(
                functionsToRunLater,
                ModalityState.any()
            )
        }
    }

    fun copyToClipboard(text: String? = null) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = StringSelection(text)
        clipboard.setContents(stringSelection, null)
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
        content.putUserData(currentProcessPrompt, myfiles.toString())
    }

    fun getUserDataFromComponent(key: Key<List<*>>, content: Content) {
        content.getUserData(key)
    }

    fun findNestedJBTabbedPanes(tabbedPane: JBTabbedPane): List<JBTabbedPane> {
        val nestedTabbedPanes = mutableListOf<JBTabbedPane>()

        fun findNestedTabbedPanesRecursive(container: Container) {
            for (currentTabbedPane in container.components) {
                if (currentTabbedPane is JBTabbedPane) {
                    nestedTabbedPanes.add(currentTabbedPane)
                    findNestedTabbedPanesRecursive(currentTabbedPane)
                } else if (currentTabbedPane is Container) {
                    findNestedTabbedPanesRecursive(currentTabbedPane)
                }
            }
        }

        findNestedTabbedPanesRecursive(tabbedPane)
        return nestedTabbedPanes
    }

    private fun addFollowUpQuestion(
        splitParts: List<String>,
        component: ChatWindowContent
    ) {
        val followUpQuestionLast =
            splitParts[splitParts.size - 1]//this magic number is to take the follow-up question from the npm analysis response part of the previous request
        if (/*(followUpQuestionLast.contains("Follow Up Question:", true) ||
                    followUpQuestionLast.contains("Follow Up:", true) ||
                    followUpQuestionLast.contains("Follow-Up Question:", true) ||
                    followUpQuestionLast.contains("Follow-Up:", true) ||
                    followUpQuestionLast.contains("Next Follow Up Question:", true) ||
                    followUpQuestionLast.contains("Next Follow-Up Question:", true)) &&*/
            GlobalData.followUpActive
        ) {
//            component.addElement("$role: -> $followUpQuestionLast")
        }
    }

    //TODO: This method is not very robust, find a better way to do this
    fun addChatWindowContentListModelToGlobalData(
        container: Container,
        text: String,
        currentRole: String,
        tabName: String,
        upperTabName: String?,
        promptList: List<String>?
    ) {
        fun findTabbedPanesRecursive(component: Component) {
            if (component is ChatWindowContent) {
                val tabCountIndex = component.getTabCountIndex()
                val parentComponent: JBTabbedPane = (component.parent.parent.parent as JBTabbedPane)
                var parentTabName: String? = null
                if (parentComponent.getTitleAt(tabCountIndex) == tabName) {
                    parentTabName = parentComponent.getTitleAt(tabCountIndex)
                }
                if (parentTabName != null) {
                    component.addElement("${GlobalData.userRole}:\n${promptList!!.joinToString("\n")}")
//                    component.addElement("$currentRole:\n${text.split("\n").dropLast(1).joinToString("\n")}")//TODO, why drop last here? I think this is causing bugs
                    component.addElement("$currentRole:\n${text.split("\n")}")
//                    val timeLine = GlobalData.upperTabNameToTimeLine[upperTabName] as TimeLine
//                    timeLine.addPointInTimeLine(
//                        Event(
//                            LocalDateTime.now(),
//                            promptList[0],
//                            true
//                        )
//                    )
//                    timeLine.addPointInTimeLine(
//                        Event(
//                            LocalDateTime.now(),
//                            "Response",
//                            false
//                        )
//                    )
                    if (currentRole == "🤖 refactor-machine") {
                        val splitParts = text.split("\n")
                        addFollowUpQuestion(splitParts, component)
//                        timeLine.addPointInTimeLine(
//                            Event(
//                                LocalDateTime.now(),
//                                "Follow Up Question:",
//                                false
//                            )
//                        )
                    }
                }
            } else if (component is Container) {
                for (child in component.components) {
                    findTabbedPanesRecursive(child)
                }
            }
        }

        findTabbedPanesRecursive(container)
    }

    fun findJBTabbedPanes(container: Container): List<JBTabbedPane> {
        val tabbedPanes = mutableListOf<JBTabbedPane>()

        fun findTabbedPanesRecursive(component: Component) {
            if (component is JBTabbedPane) {
                tabbedPanes.add(component)
            } else if (component is Container) {
                for (child in component.components) {
                    findTabbedPanesRecursive(child)
                }
            }
        }

        findTabbedPanesRecursive(container)
        return tabbedPanes
    }


    fun getPromptsAsJson(prompts: MutableMap<String, Map<String, List<String>>>): String {
        return Gson().toJson(prompts)
    }

    fun saveDataToExtensionFolder(data: String) {
        val extensionFolder = File(PathManager.getPluginsPath(), pluginId)
        if (!extensionFolder.exists()) {
            extensionFolder.mkdir()
        }
        val dataFile = File(extensionFolder, "prompt_history.json")
        dataFile.writeText(data)
    }

    fun getPromptListByKey(prompts: MutableMap<String, Map<String, List<String>>>, key: String): List<String> {
        for (outerKey in prompts.keys) {
            val innerMap = prompts[outerKey] ?: continue
            if (innerMap.containsKey(key)) {
                return if (innerMap[key]!!.isNotEmpty()) {
                    innerMap[key]!!.stream()
                        .filter(String::isNotEmpty).collect(Collectors.toList())
                } else {
                    emptyList()
                }
            }
        }
        return emptyList()
    }

    fun getToolBar(): ActionToolbar {
        val actionGroup = DefaultActionGroup()
        val project = this.getProject()!!
        val toolBar = ActionManager.getInstance().createActionToolbar(
            "MoneyPennyAI.MainPanel",
            actionGroup,
            true
        )
        actionGroup.add(SendToPromptTextEditorActionParallel(project))
        actionGroup.add(AddTextAction(project))
        actionGroup.addSeparator()
        actionGroup.add(RunPromptAction(project))
        actionGroup.add(RunAllInTabPromptAction(project))
        actionGroup.add(RunAllPromptAction(project))
        actionGroup.add(CopyPromptAction(project))
        actionGroup.addSeparator()
        actionGroup.add(
            ComboBoxEnginesAction(
                project,
                AllIcons.General.Gear,
                "Engine Selection",
                GlobalData.engineModelStrings,
                "ChatGPT Engines"
            )
        )
        actionGroup.addSeparator()
        actionGroup.add(
            ComboBoxRolesAction(
                project,
                AllIcons.CodeWithMe.Users,
                "Role Selection",
                GlobalData.roleModelStrings,
                "ChatGPT Roles"
            )
        )
        actionGroup.addSeparator()
        return toolBar
    }

    fun addPanelsToGlobalData(
        nestedPanel: JPanel,
        innerPanel: JPanel,
        tabbedPane: JBTabbedPane
    ) {
        GlobalData.nestedPanel = nestedPanel
        GlobalData.innerPanel = innerPanel
        GlobalData.selectedTabbedPane = tabbedPane
    }

    fun loadDataFromExtensionFolder(): String {
        val extensionFolder = File(PathManager.getPluginsPath(), pluginId)
        val dataFile = File(extensionFolder, "prompt_history.txt")
        return dataFile.readText()
    }

    fun createPointer(element: PsiElement) {
        SmartPointerManager.createPointer<PsiElement?>(element)
    }
//TODO use alarms to be able to cancel requests supposedly
//    fun createToolBar(toolWindow: JComponent?): JComponent {
//        val actionGroup = DefaultActionGroup()
//        actionGroup.add(SendToPromptFileFolderTreeAction(this.getProject()!!))
//        actionGroup.addSeparator()
//        val actionManager: ActionManager = ActionManager.getInstance()
//        val toolbar: ActionToolbar = actionManager.createActionToolbar("MoneyPennyAI.MainPanel", actionGroup, true)
//        toolbar.targetComponent = toolWindow
//        return toolbar.component
//    }

//    JOptionPane.showMessageDialog(
//    this@SQLPluginPanel, "Can't read file '$file'", "Error",
//    JOptionPane.ERROR_MESSAGE
//    )
////Write a kotlin class that will do the same as this one but instead of adding buttons, it will add Actions to a ToolBar, the actions will match with the buttons so "Run", "Run All" and "Copy Prompt", assume we are using Intellij Idea SDK

//    fun hola(){
//        getProject().getModelAccess().executeCommand(object : DefaultCommand() {
//            fun run() {
//                createConsoleModel()
//                addBuiltInImports()
//                loadHistory(history)
//                createEditor()
//                myFileEditor = ConsoleFileEditor(myEditor)
//            }
//        })
//    }
}