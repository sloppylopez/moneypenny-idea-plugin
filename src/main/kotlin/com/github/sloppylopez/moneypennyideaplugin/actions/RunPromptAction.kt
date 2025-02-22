package com.github.sloppylopez.moneypennyideaplugin.actions

import com.github.sloppylopez.moneypennyideaplugin.client.ChatGptMessage
import com.github.sloppylopez.moneypennyideaplugin.data.GlobalData
import com.github.sloppylopez.moneypennyideaplugin.data.GlobalData.selectedTabbedPane
import com.github.sloppylopez.moneypennyideaplugin.services.ChatGPTService
import com.github.sloppylopez.moneypennyideaplugin.services.ProjectService
import com.github.sloppylopez.moneypennyideaplugin.services.PromptService
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class RunPromptAction(private var project: Project) : AnAction() {
    private val service: ProjectService by lazy { project.service<ProjectService>() }
    private val promptService: PromptService by lazy { project.service<PromptService>() }
    private val chatGPTService: ChatGPTService by lazy { project.service<ChatGPTService>() }
//    private val progressBarFactory: ProgressBarFactory by lazy { project.service<ProgressBarFactory>() }
    private val copiedMessage = "Copied to clipboard: "

    init {
        templatePresentation.icon = AllIcons.Actions.Execute
        templatePresentation.text = "Run Prompt"
    }

    override fun actionPerformed(e: AnActionEvent) {
        project = e.project!!
        val prompt: String
//        val jProgressBar = progressBarFactory.getProgressBar()
        try {
            val tabName = selectedTabbedPane?.getTitleAt(selectedTabbedPane!!.selectedIndex)
//            progressBarFactory.addProgressBar(GlobalData.innerPanel!!, jProgressBar)
            val prompts = promptService.getPrompts()
            val promptList = service.getPromptListByKey(prompts, tabName!!).toMutableList()
            //Write code that will return the key from prompts that contains this value in the list tabName
            val upperTabName = prompts.entries.find { it.value.contains(tabName) }?.key
            val role = GlobalData.role.split(" ")[1]
            if (role == "refactor-machine" &&
                promptList.size >= 2 &&
                promptList[1].isNotBlank()
            ) {
                promptList[1] = "```\n" + promptList[1] + "\n```"
            }
            if (promptList.isNotEmpty()) {
                prompt = if (role == "refactor-machine") {
                    promptList.joinToString("\n")
                } else {
                    promptList.joinToString(" ")
                }.replace("\r\n", "\n")
                chatGPTService.sendChatPrompt(
                    prompt, createCallback(tabName), upperTabName!!, promptList
                ).whenComplete { _, _ ->
                    thisLogger().info("ChatGPTService.sendChatPrompt completed")
                }
            }
        } catch (e: Exception) {
            thisLogger().error(e.stackTraceToString())
        } finally {
//            progressBarFactory.removeProgressBar(GlobalData.innerPanel!!, jProgressBar)
        }
    }

    //TODO: needs DRYing
    private fun createCallback(tabName: String): ChatGPTService.ChatGptChoiceCallback {
        return object : ChatGPTService.ChatGptChoiceCallback {
            override fun onCompletion(
                choice: ChatGptMessage,
                prompt: String,
                upperTabName: String?,
                promptList: List<String>?
            ) {
                try {
                    var content = choice.content
                    if (service.isCodeCommented(content)) {
                        content = service.extractCommentsFromCode(content)
                    }
                    if (!content.contains("Error: No response from GPT")) {
                        service.copyToClipboard(content)
                        service.showNotification(
                            copiedMessage, content, NotificationType.INFORMATION
                        )
                        if (tabName.split(")")[1] != "No File") {
                            try {
                                val file = File(GlobalData.tabNameToFilePathMap[tabName]!!)
                                service.modifySelectedTextInEditorByFile(
                                    content, service.fileToVirtualFile(file)!!
                                )
                            } catch (e: Exception) {
                                thisLogger().info(e.stackTraceToString())
                            }
                        }
                    } else {
                        service.showNotification(
                            copiedMessage, content, NotificationType.ERROR
                        )
                    }
                    promptService.setInChat(
                        choice.content,
                        tabName,
                        GlobalData.role,
                        upperTabName,
                        promptList
                    )//In the chat window we want to display the NPL analysis as well
                } catch (e: Exception) {
                    thisLogger().error(e.stackTraceToString())
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = GlobalData.apiKey?.isNotEmpty()!!
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}