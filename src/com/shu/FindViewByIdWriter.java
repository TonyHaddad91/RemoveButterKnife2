package com.shu;


import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class FindViewByIdWriter extends WriteCommandAction.Simple {
    private static final String $_ID = "${ID}";
    private static final String $_FIELDNAME = "${FIELDNAME}";
    private static final String $_EVENT = "${EVENT}";
    private static final String $_METHOD_CALL = "${METHOD_CALL}";
    public static final String TODO_ID_WO_FIELD = "TODO: SHU can not find field for the id: ";
    public static final String TODO_ISLAND_BIND_METHOD = "TODO: SHU the bind method is not invoked.";


    final private PsiClass mClass;
    final private PsiElementFactory mFactory;
    final private List<String> idSetters;
    final private Project mProject;
    final private Map<Pair<String, String>, String> idEventMethodMap;
    final private Map<String, String> idFieldNameMap;
    private boolean noButterKnifeBindMethodFoundInClassAndFoundNoBindMethodCaller;

    final private static String TMP_OnClick =
            "if(" + $_FIELDNAME + " != null){" +
                    $_FIELDNAME + ".setOnClickListener(view ->" + $_METHOD_CALL + ");\n}";

    final private static String TMP_Unknow =
            "TODO: if(" + $_FIELDNAME + " != null){" +
                    $_FIELDNAME + ".set" + $_EVENT + "Listener(new View." + $_EVENT + "Listener () {}); " +
                    $_METHOD_CALL + " \n";

    final private static String TMP_View =
            "if(findViewById(" + $_ID + ") != null){" +
                    "findViewById(" + $_ID + ").set" + $_EVENT + "Listener(new View." + $_EVENT + "Listener() {" +
                    "                @Override" +
                    "                public void " + $_EVENT + "(View view) {" +
                    "                    " + $_METHOD_CALL + ";" +
                    "                }" +
                    "            });" +
                    "        }\n";

    final private static String TMP_View_Click =
            "if(findViewById(" + $_ID + ") != null){" +
                    "findViewById(" + $_ID + ").set" + $_EVENT + "Listener(new View." + $_EVENT + "Listener() {" +
                    "                @Override" +
                    "                public void onClick(View view) {" +
                    "                    " + $_METHOD_CALL + ";" +
                    "                }" +
                    "            });" +
                    "        }\n";

    final private static String TMP_OnCheckedChange =
            "if(" + $_FIELDNAME + " != null){" +
                    $_FIELDNAME + ".setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {" +
                    "                @Override" +
                    "                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {" +
                    "                    " + $_METHOD_CALL + ";" +
                    "                }" +
                    "            });" +
                    "        }\n";


    FindViewByIdWriter(Project project, PsiFile file, PsiClass psiClass, List<String> idSetters, PsiElementFactory
            mFactory, Map<Pair<String, String>, String> idEventMethodMap, Map<String, String> idFieldNameMap, boolean
                               noButterKnifeBindMethodFoundInClass) {
        super(project, file);
        mClass = psiClass;
        this.idSetters = idSetters;
        this.mFactory = mFactory;
        this.idEventMethodMap = idEventMethodMap;
        this.idFieldNameMap = idFieldNameMap;
        this.noButterKnifeBindMethodFoundInClassAndFoundNoBindMethodCaller = noButterKnifeBindMethodFoundInClass;
        mProject = project;

    }

    @Override
    protected void run() {
        try {
            generateBindMethod(mProject);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void generateBindMethod(Project mProject) {
        try {
            PsiMethod[] bindMethods = mClass.findMethodsByName("bind", false);
            PsiMethod[] anyMethods = mClass.findMethodsByName("bind", true);

            if (bindMethods.length == 0) {
                boolean withsuper = anyMethods.length > bindMethods.length;
                PsiMethod bindMethod = (withsuper) ?
                        mFactory.createMethodFromText
                                ("@Override protected void bind(){super.bind();}",
                                        mClass) :
                        mFactory.createMethodFromText
                                ("protected void bind(){}",
                                        mClass);
                if (withsuper) {
                    this.noButterKnifeBindMethodFoundInClassAndFoundNoBindMethodCaller = false;
                }
                appendBindMethod(bindMethod, mClass);
                mClass.addBefore(bindMethod, mClass.getRBrace());

            } else {
                appendBindMethod(bindMethods[0], mClass);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void appendBindMethod(PsiMethod bind, PsiClass mClass) {
        List<String> setters = new ArrayList<>(idSetters);
        //check if the setter is in the code
        for (PsiStatement statement : bind.getBody().getStatements()) {
            String text = statement.getText();

            // Search for setContentView()
            for (String setter : idSetters) {
                if (text.replaceAll("\\s", "").contains(setter.replaceAll("\\s", ""))) {
                    setters.remove(setter);
                }
            }
            for (Pair<String, String> idTag : idEventMethodMap.keySet()) {
                String id = idTag.first;
                String tag = idTag.second;
                String methodName = idFieldNameMap.get(id);
                if (methodName == null) continue;
                switch (tag) {
                    case "OnClick":
                        if (text.contains(methodName + ".setOnClickListener(")) {
                            idEventMethodMap.remove(idTag);
                        }
                        break;
                    case "OnCheckedChanged":
                        if (text.contains(methodName + ".setOnCheckedChangeListener(")) {
                            idEventMethodMap.remove(idTag);
                        }
                        break;

                }
            }
        }

        //add setters
        for (int i = setters.size() - 1; i >= 0; i--) {
            bind.getBody().add(mFactory.createStatementFromText(setters.get(i) + "\n", mClass));
        }

        for (Pair<String, String> idTag : idEventMethodMap.keySet()) {
            String id = idTag.first;
            String tag = idTag.second;
            if (idFieldNameMap.containsKey(id)) {

                String statement = null;
                switch (tag) {
                    case "OnClick":
                        statement = generateCode(TMP_OnClick, idTag);
                        break;
                    case "OnCheckedChanged":
                        statement = generateCode(TMP_OnCheckedChange, idTag);
                        break;

                }
                PsiElement block = (statement != null) ?
                        mFactory.createStatementFromText(statement + "\n", mClass)
                        :
                        mFactory.createCommentFromText("/* " + generateCode(TMP_Unknow, idTag) +
                                " */", mClass);

                bind.getBody().add(block);

            } else if (tag.equals("OnClick")) {
                bind.getBody().add(mFactory.createStatementFromText(generateCode(TMP_View_Click, idTag),
                        mClass));
            } else {
                bind.getBody().addAfter(mFactory.createCommentFromText("/* " + generateCode(TMP_View, idTag) +
                                " */",
                        mClass), bind.getBody().getLastBodyElement());
            }
        }
        if (noButterKnifeBindMethodFoundInClassAndFoundNoBindMethodCaller) {
            bind.getBody().addAfter(mFactory.createCommentFromText("/* " + TODO_ISLAND_BIND_METHOD + " */", null),
                    bind.getBody().getLastBodyElement());
        }

    }

    private String generateCode(String temp, Pair<String, String> idTag) {
        String method = idEventMethodMap.get(idTag);
        String field = idFieldNameMap.get(idTag.first);
        if (field != null) {
            temp = temp.replace($_FIELDNAME, field);
        }
        return temp.replace($_ID, idTag.first)
                .replace($_METHOD_CALL, method == null ? "" : method.replace("boolean", "")).replace($_EVENT, idTag.second);
    }
}
