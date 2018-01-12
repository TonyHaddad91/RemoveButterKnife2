package com.shu;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;


public class MainAction extends BaseGenerateAction {

    public MainAction() {
        super(null);
    }

    public MainAction(CodeInsightActionHandler handler) {
        super(handler);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        try {
            Project project = event.getData(PlatformDataKeys.PROJECT);
            Editor editor = event.getData(PlatformDataKeys.EDITOR);
            PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
            PsiClass mClass = getTargetClass(editor, file);
            Document document = editor.getDocument();

            new DeleteAction(project, file, document, mClass).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
