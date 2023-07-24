package com.github.sloppylopez.moneypennyideaplugin.actions

import com.github.sloppylopez.moneypennyideaplugin.helper.ToolWindowHelper.Companion.getIcon
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class PopUpHooverAction : ActionGroup() {

    companion object {
        private const val ACTION_ID = "com.github.sloppylopez.moneypennyideaplugin.actions.PopUpHooverAction"
    }

    init {
        templatePresentation.icon = getIcon("/images/MoneyPenny-Icon_13x13.jpg")
        templatePresentation.text = "MoneyPenny AI Actions"
        templatePresentation.isPopupGroup = true
    }

    // Return the actions for the submenu
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = getProjectFromEvent(e)
        return arrayOf(
            SendToPromptTextEditorAction(project!!),
            DRYSelectionAction(project)
        )//TODO Add more actions here
    }

    // Helper method to get the Project from AnActionEvent
    private fun getProjectFromEvent(e: AnActionEvent?): Project? {
        return e?.project
    }

    fun addActionsToEditor() {
        val actionManager = ActionManager.getInstance()
        val existingAction = actionManager.getAction(ACTION_ID)
        existingAction?.let {
            actionManager.unregisterAction(ACTION_ID)
        }
        actionManager.registerAction(ACTION_ID, this)
        val popupMenu = actionManager.getAction("EditorPopupMenu") as? DefaultActionGroup
        popupMenu?.addSeparator()
        popupMenu?.add(actionManager.getAction(ACTION_ID), Constraints.FIRST)
    }
}
