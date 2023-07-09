package com.github.sloppylopez.moneypennyideaplugin.services

import com.github.sloppylopez.moneypennyideaplugin.Bundle
import com.github.sloppylopez.moneypennyideaplugin.global.GlobalData
import com.intellij.notification.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBTabbedPane
import java.io.File
import java.util.*
import javax.swing.Icon
import javax.swing.JPanel

@Service(Service.Level.PROJECT)
class ProjectService {

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


    fun showDialog(
        message: String, title: String,
        buttons: Array<String>, defaultOptionIndex:
        Int, icon: Icon
    ) {
        Messages.showDialog(
            message, title,
            buttons,
            defaultOptionIndex,
            icon
        )
    }

    fun readFile(fileList: List<*>, i: Int): File? {
        try {
            if (i < fileList.size && fileList.isNotEmpty() && null != fileList[i]) {
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

    fun showMessage(
        message: String, title: String
    ) {
        Messages.showInfoMessage(
            message, title,
        )
    }

    fun highlightTextInEditor(project: Project, contentPromptText: String) {
        val editor = getCurrentEditor(project)
        editor?.let {
            val document = editor.document
            val textOffset = document.text.indexOf(contentPromptText)
            if (textOffset != -1) {
                editor.caretModel.moveToOffset(textOffset)
                editor.selectionModel.setSelection(textOffset, textOffset + contentPromptText.length)
            }
        }
    }

    private fun getCurrentEditor(project: Project): Editor? {
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

        return expandedFileList
    }


    fun getIsSnippet(normalizedFileContent: String?, normalizedSelectedText: String?) =
        normalizedFileContent != null && normalizedSelectedText?.trim() != normalizedFileContent.trim()


    fun getSelectedText(
        selectedEditor: Editor,
        selectedText: @NlsSafe String?
    ): @NlsSafe String? {
        var selectedText1 = selectedText
        val project: Project? = selectedEditor.project
        val fileEditorManager = FileEditorManager.getInstance(project!!)
        val selectedFile = fileEditorManager.selectedFiles.firstOrNull()
        if (selectedFile != null) {
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl(selectedFile.url)
            val openFileDescriptor = OpenFileDescriptor(project, virtualFile!!)
            val document = openFileDescriptor.file.let { FileDocumentManager.getInstance().getDocument(it) }
            selectedText1 = document?.text
        }
        return selectedText1
    }

    fun getTextFromToolWindow2(toolWindow: ToolWindow): String {
        val textAreaElements = mutableListOf<String>()
        val toolWindowComponent = toolWindow.component
        collectTextAreas(toolWindowComponent, textAreaElements)
        return textAreaElements.joinToString("\n")
    }

    fun getTextFromToolWindow(toolWindow: ToolWindow): String {
        val textAreaElements = mutableListOf<String>()
        val toolWindowComponent = toolWindow.component
        collectTextAreas(toolWindowComponent, textAreaElements)
        return textAreaElements.joinToString("\n")
    }

    private fun collectTextAreas(
        component: java.awt.Component,
        textAreaElements: MutableList<String>
    ) {
        if (component is javax.swing.JTextArea) {
            val text = component.text
            textAreaElements.add(text)
        }

        if (component is java.awt.Container) {
            if (component is JPanel && !component.name.isNullOrBlank()) {
                textAreaElements.add(component.name.split("\\").last())//Component name = File name
                textAreaElements.add(component.name)//Component fullpath = File fullpath
            }

            for (childComponent in component.components) {
                collectTextAreas(childComponent, textAreaElements)
            }
        }

        if (component is javax.swing.JTabbedPane) {
            val tabCount = component.tabCount
//            if (!component.name.isNullOrBlank()) {
//                textAreaElements.add(component.name)
//            }
            for (i in 1 until tabCount) {//This 1 is important, if you put 0 we get the same panel info twice
                val tabComponent = component.getComponentAt(i)
//                if (!tabComponent.name.isNullOrBlank()) {
//                    textAreaElements.add(tabComponent.name)
//                }
                collectTextAreas(tabComponent, textAreaElements)
            }
        }
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
            GlobalData.tabNameToFileMap[tabName] = file.canonicalPath
            if (contentPromptText != null) {
                GlobalData.tabNameToContentPromptTextMap[tabName] = contentPromptText
            } else {
                GlobalData.tabNameToContentPromptTextMap[tabName] = file.readText()
            }
        } else {
            tabbedPane.addTab("No File", panel)
        }

        if (contentPromptText != null && file != null) {
            val tabName = "${GlobalData.downerTabName}) ${file.name}"
            GlobalData.tabNameToContentPromptTextMap[tabName] = contentPromptText
        }
    }

    private fun getNextTabName(): String {
        return GlobalData.downerTabName++.toString()
    }

    fun getCurrentProject(): Project? {
        return com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
    }

    fun getToolWindow(): ToolWindow? {
        return ToolWindowManager.getInstance(getCurrentProject()!!).getToolWindow("MoneyPenny AI")
    }
}
