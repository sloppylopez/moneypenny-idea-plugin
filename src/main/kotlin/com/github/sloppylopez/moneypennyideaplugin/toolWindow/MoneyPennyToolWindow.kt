import com.github.sloppylopez.moneypennyideaplugin.listeners.AncestorListener
import com.github.sloppylopez.moneypennyideaplugin.services.ProjectService
import com.github.sloppylopez.moneypennyideaplugin.toolWindow.ComboBoxPanelFactory
import com.github.sloppylopez.moneypennyideaplugin.toolWindow.FileEditorManager
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

class MoneyPennyToolWindow(project: Project, private val toolWindow: ToolWindow) {

    private val comboBoxPanelFactory = project.service<ComboBoxPanelFactory>()
    private val promptPanelFactory = project.service<PromptPanelFactory>()
    private val ancestorListener = project.service<AncestorListener>()
    private val fileEditorManager = project.service<FileEditorManager>()
    private val service = project.service<ProjectService>()

    fun getContent(fileList: List<*>? = emptyList<Any>(), message: String? = null): JBPanel<JBPanel<*>> {
        return JBPanel<JBPanel<*>>().apply {
            add(moneyPennyPromptPanel(toolWindow, fileList!!, message))
        }
    }

    private fun moneyPennyPromptPanel(
        toolWindow: ToolWindow? = null,
        fileList: List<*>,
        message: String? = null
    ): JComponent {
        val tabbedPane = JBTabbedPane(JTabbedPane.BOTTOM)
        val tabCount = if (fileList.isEmpty()) 0 else fileList.size - 1
        var file: File? = null

        val changeListener = ChangeListener { _ ->
            val selectedTab = tabbedPane.selectedIndex
            val tabName = tabbedPane.getTitleAt(selectedTab)
            val filePath = ancestorListener.tabNameToFileMap[tabName]
            ancestorListener.fileEditorManager.openFileInEditor(filePath)
        }

        for (i in 0..tabCount) {
            if (fileList.isNotEmpty()) {
                file = service.readFile(fileList, i, this.javaClass.name)
            }
            val panel = JPanel(GridBagLayout())

            val gridBagConstraints = GridBagConstraints()
            gridBagConstraints.anchor = GridBagConstraints.NORTH
            gridBagConstraints.insets = JBUI.insets(2)

            for (j in 1..3) {
                val innerPanel = createInnerPanel(j, toolWindow, file, message)
                innerPanel.border = BorderFactory.createLineBorder(JBColor.GRAY, 1)
                gridBagConstraints.gridx = 0
                gridBagConstraints.gridy = j - 1
                panel.add(innerPanel, gridBagConstraints)
            }
            if (i < fileList.size && file != null) {
                val tabName = file.name
                tabbedPane.addTab(tabName, panel)
                ancestorListener.tabNameToFileMap[tabName] = file.canonicalPath
            } else {
                tabbedPane.addTab("$i", panel)
            }
        }

        tabbedPane.addChangeListener(changeListener)
        val ancestorListener = ancestorListener.getAncestorListener(tabbedPane)
        tabbedPane.addAncestorListener(ancestorListener)
        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
        return mainPanel
    }

    private fun createInnerPanel(panelIndex: Int, toolWindow: ToolWindow? = null, file: File?, message: String?): JPanel {
        val innerPanel = JPanel()
        innerPanel.layout = BoxLayout(innerPanel, BoxLayout.Y_AXIS)
        getSyntaxHighlighter(toolWindow, file)
        when (panelIndex) {
            1 -> promptPanelFactory.promptPanel(innerPanel, toolWindow, file, message)

            2 -> comboBoxPanelFactory.comboBoxPanel(innerPanel, this.promptPanelFactory)

            3 -> fileEditorManager.openFileInEditor(file?.canonicalPath)
        }
        return innerPanel
    }

    private fun getSyntaxHighlighter(toolWindow: ToolWindow?, file: File?) {
        if (toolWindow != null && file != null) {
            val language = Language.findLanguageByID("java")
            service.logInfo("MoneyPennyToolWindow", language.toString())
            if (language != null) {
                val hl = SyntaxHighlighterFactory.getSyntaxHighlighter(
                    language,
                    toolWindow.project,
                    service.fileToVirtualFile(file)
                )
                service.logInfo("MoneyPennyToolWindow", hl.toString())
            }
        }
    }
}
