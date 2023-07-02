package com.github.sloppylopez.moneypennyideaplugin.toolWindow

import FileEditorFactory
import ProgressPanelFactory
import PromptPanelFactory
import com.github.sloppylopez.moneypennyideaplugin.services.ProjectService
import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.*
import javax.swing.event.ChangeListener

private lateinit var tabbedPane: JBTabbedPane // Declare com.github.sloppylopez.moneypennyideaplugin.toolWindow.tabbedPane at the class level
private var showDialog: Boolean = false

class MoneyPennyToolWindow(project: Project, toolWindow: ToolWindow) {

    private val comboBoxPanelFactory = project.service<ComboBoxPanelFactory>()
    private val progressPanelFactory = project.service<ProgressPanelFactory>()
    private val fileEditorFactory = project.service<FileEditorFactory>()
    private val fileEditorFactory2 = project.service<FileEditorFactory2>()
    private val promptPanelFactory = project.service<PromptPanelFactory>()
    private val service = project.service<ProjectService>()
    private val currentToolWindow = toolWindow

    fun getContent(
        fileList: List<*>? = emptyList<Any>()
    ) = JBPanel<JBPanel<*>>().apply {
        add(moneyPennyPromptPanel(currentToolWindow, fileList!!))
    }

    private fun moneyPennyPromptPanel(
        toolWindow: ToolWindow? = null,
        fileList: List<*>
    ): JComponent {
        val tabbedPane = JBTabbedPane()
        val tabCount = if (fileList.isEmpty()) 0 else fileList.size - 1
        var file: File? = null

        val changeListener = ChangeListener { _ ->
            val selectedTab = tabbedPane.selectedIndex
            val tabName = tabbedPane.getTitleAt(selectedTab)
            JOptionPane.showMessageDialog(tabbedPane, "Selected Tab1: $tabName")
        }

        for (i in 0..tabCount) {
            if (fileList.isNotEmpty()) {
                file = readFile(fileList, i)
            }
            val panel = JPanel(GridBagLayout())

            val gridBagConstraints = GridBagConstraints()
            gridBagConstraints.anchor = GridBagConstraints.NORTH
            gridBagConstraints.insets = JBUI.insets(2)

            for (j in 1..3) {
                val innerPanel = createInnerPanel(j, toolWindow, file)
                innerPanel.border = BorderFactory.createLineBorder(JBColor.GRAY, 1)
                gridBagConstraints.gridx = 0
                gridBagConstraints.gridy = j - 1
                panel.add(innerPanel, gridBagConstraints)
            }
            if (i < fileList.size && file != null) {
                tabbedPane.addTab(file.name, panel)
            } else {
                tabbedPane.addTab("$i", panel)
            }
        }

        tabbedPane.addChangeListener(changeListener)
        tabbedPane.addAncestorListener(object : javax.swing.event.AncestorListener {
            override fun ancestorAdded(e: javax.swing.event.AncestorEvent?) {
                service.logInfo("MoneyPennyToolWindow", "Ancestor Added")
                val selectedTab = tabbedPane.selectedIndex
                val tabName = tabbedPane.getTitleAt(selectedTab)
//                if (showDialog) {
                    JOptionPane.showMessageDialog(tabbedPane, "Selected Tab2: $tabName")
//                    showDialog = true // Set the flag to true after showing the dialog
//                }
            }

            override fun ancestorMoved(e: javax.swing.event.AncestorEvent?) {
                service.logInfo("MoneyPennyToolWindow", "Ancestor Moved")
            }

            override fun ancestorRemoved(e: javax.swing.event.AncestorEvent?) {
                service.logInfo("MoneyPennyToolWindow", "Ancestor Removed")
            }
        })
        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(tabbedPane, BorderLayout.NORTH)
//        showDialog = true // Set the flag to true after showing the dialog
        return mainPanel
    }

    private fun readFile(fileList: List<*>, i: Int): File? {
        try {
            if (i < fileList.size && fileList.isNotEmpty() && null != fileList[i]) {
                val file = fileList[i] as File
                service.logInfo("MoneyPennyToolWindow", "File $file")
                return file
            } else {
                service.logInfo("MoneyPennyToolWindow", "File is null")
            }
        } catch (e: Exception) {
            service.logError("MoneyPennyToolWindow", e)
        }
        return null
    }

    private fun createInnerPanel(
        panelIndex: Int,
        toolWindow: ToolWindow? = null,
        file: File?
    ): JPanel {
        val innerPanel = JPanel()
        innerPanel.layout = BoxLayout(innerPanel, BoxLayout.Y_AXIS)
        getSyntaxHighlighter(toolWindow, file)
        when (panelIndex) {
            1 -> promptPanelFactory
                .promptPanel(innerPanel, toolWindow, file)

            2 -> comboBoxPanelFactory
                .comboBoxPanel(innerPanel, this.promptPanelFactory)

            3 -> fileEditorFactory2
                .openFileInEditor(file?.canonicalPath)
        }

        return innerPanel
    }

    private fun getSyntaxHighlighter(toolWindow: ToolWindow?, file: File?) {
        if (toolWindow != null && file != null) {
            val language = Language.findLanguageByID("java")
            service.logInfo("MoneyPennyToolWindow", language.toString())
            if (language != null) {
                val hl = SyntaxHighlighterFactory
                    .getSyntaxHighlighter(
                        language,
                        toolWindow.project,
                        service.fileToVirtualFile(file)
                    )
                service.logInfo("MoneyPennyToolWindow", hl.toString())
            }
        }
    }
}
