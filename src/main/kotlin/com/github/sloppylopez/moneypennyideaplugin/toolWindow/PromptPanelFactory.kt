package com.github.sloppylopez.moneypennyideaplugin.toolWindow

import TextAreaFactory
import com.github.sloppylopez.moneypennyideaplugin.services.ProjectService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTextArea


@Service(Service.Level.PROJECT)
class PromptPanelFactory(project: Project) : DropTargetAdapter() {
    private var promptPanel = JPanel()
    private var currentProject = project
    private var currentToolWindow: ToolWindow? = null
    private val checkBoxFactory = project.service<CheckBoxFactory>()
    private val radioButtonFactory = project.service<RadioButtonFactory>()
    private val textAreaFactory = project.service<TextAreaFactory>()
    private val service = project.service<ProjectService>()
    private var tabCounter = 0
    private var prePromptTextArea: JTextArea? = JTextArea()
    private var contentPromptTextArea: JTextArea? = JTextArea()
    private var postPromptTextArea: JTextArea? = JTextArea()

    fun promptPanel(
        panel: JPanel,
        toolWindow: ToolWindow? = null,
        file: File?,
        contentPromptText: String?
    ) {
        try {
            promptPanel = panel
            currentToolWindow = toolWindow

            prePromptTextArea = createPaddedTextArea("", 2, 79)
            contentPromptTextArea = createPaddedTextArea(
                "Paste text, drag a file, copy folder path...",
                10,
                79,
                "C:\\Users\\sergi\\PycharmProjects2\\moneypenny-idea-plugin\\src\\main\\resources\\images\\pluginIcon_BIG.png"
            )
            postPromptTextArea = createPaddedTextArea("", 5, 79)

            radioButtonFactory.radioButtonsPanel(panel, prePromptTextArea!!)

            if (contentPromptTextArea != null) {
                val prePromptScrollPane = JBScrollPane(prePromptTextArea)
                panel.add(prePromptScrollPane)

                val contentPromptScrollPane = JBScrollPane(contentPromptTextArea)
                contentPromptTextArea?.text = getText(file, contentPromptText)
                panel.add(contentPromptScrollPane)

                val postPromptScrollPane = JBScrollPane(postPromptTextArea)
                panel.add(postPromptScrollPane)

                checkBoxFactory.checkboxesPanel(panel, postPromptTextArea!!)

                contentPromptTextArea?.dropTarget = DropTarget(contentPromptTextArea, this)
            }
        } catch (e: Exception) {
            thisLogger().error(e)
        }
    }

    private fun createPaddedTextArea(
        text: String,
        rows: Int,
        columns: Int,
        imageBackground: String? = null
    ): JTextArea {
        val textArea = textAreaFactory.createTextArea(text, rows, columns, imageBackground)
        val padding = JBUI.insets(5) // Adjust the padding values as needed
        val border = BorderFactory.createEmptyBorder(padding.top, padding.left, padding.bottom, padding.right)
        textArea.border = border
        return textArea
    }


    private fun getText(file: File?, message: String?): String {
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

    override fun drop(dtde: DropTargetDropEvent) {
        dtde.acceptDrop(DnDConstants.ACTION_COPY)
        val transferable: Transferable = dtde.transferable

        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {//TODO: Use DataFlavor.getTextPlainUnicodeFlavor()??? better
            val fileList = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
            createContentFromFiles(fileList)
        }
    }

    fun createContentFromFiles(fileList: List<*>) {
        try {
            val expandedFileList = service.expandFolders(fileList)
            val moneyPennyToolWindow = MoneyPennyToolWindow(currentProject, currentToolWindow!!)
            val contentTab = ContentFactory.getInstance()
                .createContent(
                    moneyPennyToolWindow.getContent(expandedFileList, null),
                    getDisplayName(expandedFileList),
                    true
                )
            val contentManager = currentToolWindow!!.contentManager
            contentTab.setDisposer {
                thisLogger().info("contentTab is disposed, contentCount: ${contentManager.contentCount}")
            }
            contentManager.addContent(contentTab, 0)
            contentManager.setSelectedContent(contentTab)
            expandedFileList.forEach {
                val fileContents = String(Files.readAllBytes(File(it.path).toPath()))
                service.highlightTextInEditor(currentProject, fileContents)
            }
        } catch (e: Exception) {
            thisLogger().error("PromptPanelFactory: ", e)
        }
    }

    private fun getDisplayName(expandedFileList: List<File>): String {
        val prefix = if (expandedFileList.isEmpty()) "Prompt" else
            "${expandedFileList.size} Arch"
        return "${getNextTabName()}) $prefix"
    }

    private fun getNextTabName(): String {
        return tabCounter++.toString()
    }

    fun sendToContentPrompt(
        editor: Editor?,
        file: File?,
        isSnippet: Boolean? = false,
    ) {
        editor?.let { selectedEditor ->
            var selectedText = selectedEditor.selectionModel.selectedText
            if (selectedText.isNullOrEmpty()) {
                selectedText = service.getSelectedText(selectedEditor, selectedText)
            }
            if (!selectedText.isNullOrEmpty()) {
                try {
                    val moneyPennyToolWindow = MoneyPennyToolWindow(currentProject, currentToolWindow!!)
                    val content = ContentFactory.getInstance().createContent(
                        moneyPennyToolWindow.getContent(listOf(file), selectedText),
                        if (isSnippet == true) "Snippet_${getNextTabName()}" else
                            "${getNextTabName()}) 1 Arch",
                        true
                    )

                    val contentManager = currentToolWindow!!.contentManager
                    contentManager.addContent(content, 0)
                    contentManager.setSelectedContent(content) // Set the newly added content as selected
                } catch (e: Exception) {
                    thisLogger().error(e)
                }
            }
        }
    }

    fun getCombinedText(): String {
        val prePromptText = prePromptTextArea?.text ?: ""
        val contentPromptText = contentPromptTextArea?.text ?: ""
        val postPromptText = postPromptTextArea?.text ?: ""
        return prePromptText + contentPromptText + postPromptText
    }
}
