package com.shu;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.hash.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DeleteAction extends WriteCommandAction.Simple {
    final private List<Integer> linesToDelete = new ArrayList<>();
    final private Map<Integer, String> bindToChange = new HashMap<>();
    final private Project project;
    final private PsiFile file;
    final private String[] lines;
    final private PsiClass mClass;
    final private PsiElementFactory mFactory;
    final private Document document;
    final private Map<String, String> fieldNamePrefixAndIdMap = new LinkedHashMap<>();
    final private Map<Integer, String> lineFieldTypeNameMap = new LinkedHashMap<>();
    final private Map<String, String> idFieldNameMap = new LinkedHashMap<>();
    final private Map<Pair<String, String>, String> idEventMethodMap = new ConcurrentHashMap<>();
    private boolean foundBindMethodCaller = false;


    DeleteAction(Project project, PsiFile file, Document document, PsiClass psiClass) {
        super(project, file);
        this.document = document;
        this.project = project;
        this.file = file;
        this.mClass = psiClass;
        mFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        lines = document.getText().split("\n");
    }

    private void scanButterKnifeBindMethod() {
        bindToChange.clear();
        for (int i = 0; i < lines.length; i++) {
            String text = lines[i];
            String pack = lines[i].replaceAll("\\s", "");

            if (pack.contains("ButterKnife.bind(this)")) {
                bindToChange.put(i, text.replace("ButterKnife.", "").replace("this", ""));
            } else if (text.contains("ButterKnife.bind(")) {
                bindToChange.put(i, text.replace("ButterKnife.", ""));
            }
            if (text.contains("bind(") && !text.contains("void bind(")) {
                this.foundBindMethodCaller = true;
            }

        }
    }

    private final static String[] butterKnifeImports = {
            "import butterknife.Bind;",
            "import butterknife.InjectView;",
            "import butterknife.ButterKnife;",
            "import butterknife.OnClick;",
            "import butterknife.BindView;"};

    boolean foundButterKnifeImport = false;

    private void deleteImport() {
        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            if (lines[lineNum].equals(butterKnifeImports[0]) || lines[lineNum].equals(butterKnifeImports[1]) || lines[lineNum].equals(butterKnifeImports[2]) || lines[lineNum]
                    .equals(butterKnifeImports[3])) {
                linesToDelete.add(lineNum);
            }
            if (lines[lineNum].trim().startsWith("import butterknife")) {
                foundButterKnifeImport = true;
            }
        }
    }

    private void scanBindViewsTwoLineCase() {
        String text = "^@(BindView|BindString|BindDrawable|BindColor|BindDimen)\\(R2?.id.*\\)$";
        Pattern pattern = Pattern.compile(text);
        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            Matcher matcher = pattern.matcher(lines[lineNum].trim());
            lines[lineNum] = lines[lineNum].trim();
            if (matcher.find()) {
                String id = lines[lineNum].substring(lines[lineNum].indexOf("(") + 1, lines[lineNum].length() - 1);
                String[] fieldNameLine = lines[lineNum + 1].trim().split(" ");
                int ind = fieldNameLine.length - 1;
                String fieldName = fieldNameLine[ind].substring(0, fieldNameLine[ind].length() - 1);
                String fieldNameEqCast = fieldName + " = " + "(" + fieldNameLine[ind - 1] + ")";
                fieldNamePrefixAndIdMap.put(fieldNameEqCast, id);
                idFieldNameMap.put(id, fieldName);
                linesToDelete.add(lineNum);
            }
        }
    }

    private void scanBindViewsSingleLineCase() {
        String text = "@(BindView|BindString|BindDrawable|BindColor|BindDimen)\\(R2?.id.*\\)*;";
        Pattern pattern = Pattern.compile(text);

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            Matcher matcher = pattern.matcher(lines[lineNum]);
            if (matcher.find()) {
                String id = lines[lineNum].substring(lines[lineNum].indexOf("(") + 1, lines[lineNum].indexOf(")"));
                String fieldPart = lines[lineNum].substring(lines[lineNum].indexOf(")") + 1).trim();
                String[] fieldParts = fieldPart.split(" ");
                int ind = fieldParts.length - 1;
                String fieldName = fieldParts[ind].trim();
                fieldName = fieldName.substring(0, fieldName.length() - 1);
                idFieldNameMap.put(id, fieldName);
                String fieldType = fieldParts[ind - 1];
                String fieldModifier = (ind - 2 == 0) ? fieldParts[0].trim() + " " : "";
                lineFieldTypeNameMap.put(lineNum, fieldModifier + fieldType + " " + fieldName);
                fieldNamePrefixAndIdMap.put(fieldName + " = " + "(" + fieldType + ")", id);
                System.out.print(fieldType + "--" + fieldName);
            }
        }
    }

    private void scanOnclick() {
        String text = "^@(On)[a-z,A-Z]*\\(R2?.id.*\\)$";
        Pattern pattern = Pattern.compile(text);
        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            Matcher matcher = pattern.matcher(lines[lineNum].trim());
            lines[lineNum] = lines[lineNum].trim();
            if (matcher.find()) {
                String tag = lines[lineNum].substring(lines[lineNum].indexOf("@") + 1, lines[lineNum].indexOf("("))
                        .trim();
                String id = lines[lineNum].substring(lines[lineNum].indexOf("(") + 1, lines[lineNum].length() - 1);
                String methodSyntax = lines[lineNum + 1].substring(lines[lineNum + 1].indexOf("void ") + 5, lines[lineNum
                        + 1].indexOf('{'))
                        .trim();

                if (methodSyntax.length() > 3) {
                    idEventMethodMap.put(new Pair(id, tag), methodSyntax);
                }

                if (lineNum > 1) {
                    String line = lines[lineNum - 1].trim();
                    if (line.length() > 5) {
                        if (line.replace("@Optional", "").isEmpty()) {
                            linesToDelete.add(lineNum - 1);
                        }
                    }
                }
                linesToDelete.add(lineNum);
            }
        }
    }

    @Override
    protected void run() {
        try {
            deleteImport();

            if (!foundButterKnifeImport) {
                return;
            }
            //scan annotations
            scanButterKnifeBindMethod();
            scanBindViewsSingleLineCase();
            scanBindViewsTwoLineCase();
            scanOnclick();

            //butterKnife.bind to bind
            for (Integer num : bindToChange.keySet()) {
                int deleteStart = document.getLineStartOffset(num);
                int deleteEnd = document.getLineEndOffset(num);
                document.replaceString(deleteStart, deleteEnd, bindToChange.get(num));
            }

            //delete the annotation in the single line case
            for (Map.Entry<Integer, String> entry : lineFieldTypeNameMap.entrySet()) {
                int deleteStart = document.getLineStartOffset(entry.getKey());
                int deleteEnd = document.getLineEndOffset(entry.getKey());
                document.replaceString(deleteStart, deleteEnd, "\t" + entry.getValue() + ";");
            }

            linesToDelete.sort(Collections.reverseOrder());
            //delete the whole line in the two line case.
            for (Integer num : linesToDelete) {
                int deleteStart = document.getLineStartOffset(num);
                int deleteEnd = document.getLineEndOffset(num);
                int extra = document.getLineSeparatorLength(num);
                document.deleteString(deleteStart, deleteEnd + extra);
            }
            PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
            manager.commitDocument(document);

            //generate code
            createFindViewByIdCode();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createFindViewByIdCode() {
        List<String> idSetters = new ArrayList<>();
        for (Map.Entry<String, String> entry : fieldNamePrefixAndIdMap.entrySet()) {
            String text = entry.getKey() + "findViewById(" + entry.getValue() + ");";
            idSetters.add(text);
        }
        new FindViewByIdWriter(project, file, mClass, idSetters, mFactory, idEventMethodMap, idFieldNameMap,
                bindToChange.isEmpty() && !foundBindMethodCaller)
                .execute();
    }
}

