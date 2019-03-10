package de.peeeq.wurstscript.translation.jvm;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import de.peeeq.wurstscript.WurstOperator;
import de.peeeq.wurstscript.ast.AstElementWithNameId;
import de.peeeq.wurstscript.ast.Element;
import de.peeeq.wurstscript.ast.OnDestroyDef;
import de.peeeq.wurstscript.ast.WPackage;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.parser.WPos;
import de.peeeq.wurstscript.types.TypesHelper;
import de.peeeq.wurstscript.utils.Constants;
import de.peeeq.wurstscript.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static de.peeeq.wurstscript.translation.imtranslation.FunctionFlagEnum.IS_ABSTRACT;
import static de.peeeq.wurstscript.translation.imtranslation.FunctionFlagEnum.IS_INTERFACE;
import static de.peeeq.wurstscript.types.TypesHelper.imInt;
import static org.objectweb.asm.Opcodes.*;

/**
 *
 */
public class JvmTranslation {

    /**
     * target version for translation
     */
    public static final int JAVA_VERSION = V11;
    private static Label currentEndLoopLabel;
    private ImProg prog;
    private final Path outputFolder;
    private ImmutableMultimap<ImClass, ImMethod> methodByClass;
    private Map<ImVar, Integer> localVars = new HashMap<>();
    // signatures of functions already added to the current class
    private Set<String> currentClassFunctions = new HashSet<>();

    public JvmTranslation(ImProg prog, Path outputFolder) {
        this.prog = prog;
        this.outputFolder = outputFolder;
    }

    static class JPackage {
        final String name;
        private final WPos source;

        JPackage(String name, WPos source) {
            this.name = name;
            this.source = source;
        }

        public WPos getSource() {
            return source;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JPackage jPackage = (JPackage) o;
            return Objects.equals(name, jPackage.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public void translate() {
        try {
            normalizeNames();

            Multimap<JPackage, ImFunction> functionsByPackage =
                    prog.getFunctions().stream()
                            .collect(Utils.groupBy(this::getPackage));
            Multimap<JPackage, ImVar> varsByPackage =
                    prog.getGlobals().stream()
                            .collect(Utils.groupBy(this::getPackage));
            Multimap<JPackage, ImClass> classesByPackage =
                    prog.getClasses().stream()
                            .collect(Utils.groupBy(this::getPackage));

            Multimap<JPackage, ImTupleType> tuplesByPackage =
                    prog.getTupleTypes().stream()
                            .collect(Utils.groupBy(this::getPackage));

            methodByClass =
                    prog.getMethods().stream()
                            .collect(Utils.groupBy(f -> f.getMethodClass().getClassDef()));
            Set<JPackage> packages = new LinkedHashSet<>(functionsByPackage.keys());
            packages.addAll(varsByPackage.keys());
            packages.addAll(classesByPackage.keys());

            // create one class per package
            for (JPackage p : packages) {
                translatePackage(p, functionsByPackage.get(p), varsByPackage.get(p), classesByPackage.get(p), tuplesByPackage.get(p));
            }


            // group functions and globals by package

            // translate functions and globals as statics in the package

            // group methods by class

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * uses the original names when possible instead of the mangled names
     */
    private void normalizeNames() {
        for (ImVar g : prog.getGlobals()) {
            Element trace = g.attrTrace();
            if (trace instanceof AstElementWithNameId) {
                g.setName(((AstElementWithNameId) trace).getNameId().getName());
            }
        }
        for (ImFunction f : prog.getFunctions()) {
            Element trace = f.attrTrace();
            if (trace instanceof AstElementWithNameId) {
                f.setName(((AstElementWithNameId) trace).getNameId().getName());
            }
        }
        for (ImClass c : prog.getClasses()) {
            for (ImVar g : c.getFields()) {
                Element trace = g.attrTrace();
                if (trace instanceof AstElementWithNameId) {
                    g.setName(((AstElementWithNameId) trace).getNameId().getName());
                }
            }
            for (ImFunction f : c.getFunctions()) {
                Element trace = f.attrTrace();
                if (trace instanceof AstElementWithNameId) {
                    f.setName(((AstElementWithNameId) trace).getNameId().getName());
                }
            }
            for (ImMethod m : c.getMethods()) {
                Element trace = m.attrTrace();
                if (trace instanceof AstElementWithNameId) {
                    m.setName(((AstElementWithNameId) trace).getNameId().getName());
                }
                if (trace instanceof OnDestroyDef) {
                    m.setName("__onDestroy");
                }
            }

            Element trace = c.attrTrace();
            if (trace instanceof AstElementWithNameId) {
                c.setName(((AstElementWithNameId) trace).getNameId().getName());
            }
        }
    }

    private JPackage getPackage(de.peeeq.wurstscript.jassIm.Element element) {
        return getPackage(element.attrTrace());
    }

    private JPackage wurstMain = new JPackage("WurstMain", null);

    private JPackage getPackage(Element element) {
        while (element != null) {
            if (element instanceof WPackage) {
                return new JPackage(((WPackage) element).getName(), element.attrSource());
            }
            element = element.getParent();
        }
        return wurstMain;
    }

    private ImClass getElemClass(de.peeeq.wurstscript.jassIm.Element element) {
        while (element != null) {
            if (element instanceof ImClass) {
                return (ImClass) element;
            }
            element = element.getParent();
        }
        return null;
    }

    private void translatePackage(JPackage p, Collection<ImFunction> imFunctions, Collection<ImVar> imVars, Collection<ImClass> imClasses, Collection<ImTupleType> imTupleTypes) throws IOException {
        ClassWriter classWriter = new WurstClassWriter(); // ClassWriter.COMPUTE_MAXS
        classWriter.visit(V11, ACC_PUBLIC | ACC_SUPER, p.name, null, "java/lang/Object", null);
        if (p.getSource() != null) {
            classWriter.visitSource(p.getSource().getFile(), null);
        }

        if (p.equals(wurstMain)) {
            new WurstStandardFunctions(wurstMain).addStandardFunctions(classWriter);
        }

        for (ImClass c : imClasses) {
            translateClass(p, classWriter, c);
        }

        for (ImTupleType tt : imTupleTypes) {
            translateTupleType(p, classWriter, tt);
        }

        for (ImVar v : imVars) {
            translateStaticVar(classWriter, v);
        }
        for (ImFunction f : imFunctions) {
            translateFunc(classWriter, f, ACC_PUBLIC | ACC_STATIC, f.getName());
        }

        createPackageInitFunction(classWriter, p, imVars);

        classWriter.visitEnd();
        byte[] bytes = classWriter.toByteArray();
        Files.write(outputFolder.resolve(p.name + ".class"), bytes);
        System.out.println("written " + outputFolder.resolve(p.name + ".class").toFile().getAbsolutePath());

        for (ImClass c : imClasses) {
            translateInnerClass(p, c);
        }
    }


    private void translateClass(JPackage p, ClassWriter outerClassWriter, ImClass c) {
        String name = className(c, p);
        outerClassWriter.visitNestMember(name);
        outerClassWriter.visitInnerClass(name, p.name, c.getName(), ACC_STATIC);
    }

    private void translateInnerClass(JPackage p, ImClass c) throws IOException {
        currentClassFunctions.clear();
        String name = className(c, p);
        ClassWriter classWriter = new WurstClassWriter();
        // TODO use correct super-class
        Optional<ImClass> superClass = c.getSuperClasses().stream()
                .map(ImClassType::getClassDef)
                .filter(st -> !st.getFlags().contains(IS_INTERFACE))
                .collect(Utils.oneAndOnly());
        String superClassDescr = superClass.map(this::classDescriptor).orElse("java/lang/Object");

        String[] interfaces = c.getSuperClasses().stream()
                .map(ImClassType::getClassDef)
                .filter(cc -> cc.getFlags().contains(IS_INTERFACE))
                .map(this::classDescriptor)
                .toArray(String[]::new);

        int access = ACC_PUBLIC;
        if (c.getFlags().contains(IS_INTERFACE)) {
            access |= ACC_ABSTRACT | ACC_INTERFACE;
        } else {
            access |= ACC_SUPER;
            if (c.getFlags().contains(IS_ABSTRACT)) {
                access |= ACC_ABSTRACT;
            }
        }

        classWriter.visit(JAVA_VERSION, access, name, null, superClassDescr, interfaces);
        if (p.getSource() != null) {
            classWriter.visitSource(c.attrTrace().attrSource().getFile(), null);
        }
        classWriter.visitNestHost(p.name);
        classWriter.visitInnerClass(name, p.name, c.getName(), ACC_PUBLIC | ACC_STATIC);

        if (!c.getFlags().contains(IS_INTERFACE)) {
            createInitFunction(classWriter, c, superClassDescr);
        }

        for (ImVar v : c.getFields()) {
            translateField(classWriter, v);
        }

        for (ImMethod method : c.getMethods()) {
            translateMethod(classWriter, method);
        }

        for (ImFunction func : c.getFunctions()) {
            if (!func.getBody().isEmpty()) {
                translateFunc(classWriter, func, ACC_PUBLIC | ACC_STATIC, func.getName());
            }
        }

        classWriter.visitEnd();
        byte[] bytes = classWriter.toByteArray();
        Files.write(outputFolder.resolve(name + ".class"), bytes);
        System.out.println("written " + outputFolder.resolve(name + ".class").toFile().getAbsolutePath());
    }


    private void translateTupleType(JPackage p, ClassWriter outerClassWriter, ImTupleType tt) throws IOException {
        String name = className(tt, p);
        outerClassWriter.visitNestMember(name);
        outerClassWriter.visitInnerClass(name, p.name, tt.getName(), ACC_STATIC);

        ClassWriter classWriter = new WurstClassWriter();
        // TODO use correct super-class
        String superClassDescr = "java/lang/Object";


        int access = ACC_PUBLIC | ACC_SUPER;

        String[] interfaces = {};
        classWriter.visit(JAVA_VERSION, access, name, null, superClassDescr, interfaces);
        classWriter.visitNestHost(p.name);
        classWriter.visitInnerClass(name, p.name, tt.getName(), ACC_PUBLIC | ACC_STATIC);


        for (ImVar v : tt.getParameters()) {
            translateField(classWriter, v);
        }

        // create constructor
        createTupleInitFunction(classWriter, name, tt, superClassDescr);


        // create equals
        createTupleEquals(classWriter, name, tt);


        classWriter.visitEnd();
        byte[] bytes = classWriter.toByteArray();
        Files.write(outputFolder.resolve(name + ".class"), bytes);
        System.out.println("written " + outputFolder.resolve(name + ".class").toFile().getAbsolutePath());

    }

    private void createTupleEquals(ClassWriter classWriter, String name, ImTupleType tt) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 1);
        Label notReferenceEquals = new Label();
        methodVisitor.visitJumpInsn(IF_ACMPNE, notReferenceEquals);
        // if reference equals return true
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitLabel(notReferenceEquals);
        methodVisitor.visitLineNumber(15, notReferenceEquals);
        methodVisitor.visitVarInsn(ALOAD, 1);
        Label returnFalse = new Label();
        methodVisitor.visitJumpInsn(IFNULL, returnFalse);

        // compare classes
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        Label ifSameClass = new Label();
        methodVisitor.visitJumpInsn(IF_ACMPEQ, ifSameClass);
        // if other is null or classes are different return false:
        methodVisitor.visitLabel(returnFalse);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitInsn(IRETURN);

        // if classes are the same compare fields
        methodVisitor.visitLabel(ifSameClass);
        // cast to right class
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitTypeInsn(CHECKCAST, name);
        methodVisitor.visitVarInsn(ASTORE, 2);

        for (ImVar p : tt.getParameters()) {
            // load this.p
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, name, p.getName(), translateType(p.getType()));
            // load other.p
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitFieldInsn(GETFIELD, name, p.getName(), translateType(p.getType()));
            if (TypesHelper.isIntType(p.getType())) {
                methodVisitor.visitJumpInsn(IF_ICMPNE, returnFalse);
            } else if (TypesHelper.isFloatType(p.getType())) {
                methodVisitor.visitInsn(FCMPL);
                methodVisitor.visitJumpInsn(IFNE, returnFalse);
            } else {
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                methodVisitor.visitJumpInsn(IFEQ, returnFalse);
            }
        }
        // all equal: return true
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitMaxs(10, 10);
        methodVisitor.visitEnd();
    }

    private void createInitFunction(ClassWriter classWriter, ImClass c, String superClassDescr) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, superClassDescr, "<init>", "()V", false);
        // initialize arrays
        for (ImVar v : c.getFields()) {
            if (v.getType() instanceof ImArrayType) {
                ImArrayType at = (ImArrayType) v.getType();
                methodVisitor.visitLdcInsn(Constants.MAX_ARRAY_SIZE);
                ImType entryType = at.getEntryType();
                newArrayInstruction(methodVisitor, entryType);
                methodVisitor.visitFieldInsn(PUTFIELD, classDescriptor(c), v.getName(), translateType(at));
            }
            if (v.getType() instanceof ImArrayTypeMulti) {
                ImArrayTypeMulti at = (ImArrayTypeMulti) v.getType();
                if (at.getArraySize().size() != 1) {
                    throw new RuntimeException("TODO " + at + " sizes: " + at.getArraySize());
                }
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitLdcInsn(at.getArraySize().get(0));
                ImType entryType = at.getEntryType();
                newArrayInstruction(methodVisitor, entryType);
                methodVisitor.visitFieldInsn(PUTFIELD, classDescriptor(c), v.getName(), translateType(at));
            }
        }
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
    }

    private void createTupleInitFunction(ClassWriter classWriter, String className, ImTupleType tt, String superClassDescr) {

        String sig = getTupleConstructorSignature(tt);

        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", sig, null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, superClassDescr, "<init>", "()V", false);
        // initialize fields from params
        for (int i = 0; i < tt.getParameters().size(); i++) {
            ImVar param = tt.getParameters().get(i);

            // load this
            methodVisitor.visitVarInsn(ALOAD, 0);
            // load the parameter
            methodVisitor.visitVarInsn(getLoadInstruction(param.getType()), 1 + i);
            methodVisitor.visitFieldInsn(PUTFIELD, className, param.getName(), translateType(param.getType()));
        }

        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
    }

    @NotNull
    private String getTupleConstructorSignature(ImTupleType tt) {
        return getSignatureDescriptor(Utils.mapped(tt.getParameters(), ImVar::getType), JassIm.ImVoid());
    }

    private void createPackageInitFunction(ClassWriter classWriter, JPackage p, Collection<ImVar> imVars) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        methodVisitor.visitCode();
        // initialize arrays
        for (ImVar v : imVars) {
            if (v.getType() instanceof ImArrayType) {
                ImArrayType at = (ImArrayType) v.getType();
                methodVisitor.visitLdcInsn(Constants.MAX_ARRAY_SIZE);
                ImType entryType = at.getEntryType();
                newArrayInstruction(methodVisitor, entryType);
                methodVisitor.visitFieldInsn(PUTSTATIC, p.name, v.getName(), translateType(at));
            }
            if (v.getType() instanceof ImArrayTypeMulti) {
                ImArrayTypeMulti at = (ImArrayTypeMulti) v.getType();
                throw new RuntimeException("TODO " + at + " sizes: " + at.getArraySize());
            }
        }
        // initialize indexMap in WurstMain
        if (p.equals(wurstMain)) {
            methodVisitor.visitTypeInsn(NEW, "java/util/HashMap");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
            methodVisitor.visitFieldInsn(PUTSTATIC, wurstMain.name, "indexMap", "Ljava/util/Map;");
        }
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
    }

    private void newArrayInstruction(MethodVisitor methodVisitor, ImType entryType) {
        if (entryType.equalsType(imInt())) {
            methodVisitor.visitIntInsn(NEWARRAY, T_INT);
        } else if (entryType.equalsType(TypesHelper.imBool())) {
            methodVisitor.visitIntInsn(NEWARRAY, T_BOOLEAN);
        } else if (entryType.equalsType(TypesHelper.imReal())) {
            methodVisitor.visitIntInsn(NEWARRAY, T_FLOAT);
        } else {
            methodVisitor.visitTypeInsn(ANEWARRAY, classDescriptor(entryType));
        }
    }

    private void translateMethod(ClassWriter classWriter, ImMethod method) {
        ImFunction impl = method.getImplementation();
        int acc = ACC_PUBLIC;
        if (method.getIsAbstract()) {
            acc |= ACC_ABSTRACT;
        }
        translateFunc(classWriter, impl, acc, method.getName());
    }

    private void translateField(ClassWriter classWriter, ImVar v) {
        FieldVisitor fieldVisitor = classWriter.visitField(0, v.getName(), translateType(v.getType()), null, null);
        fieldVisitor.visitEnd();
    }

    private void translateStaticVar(ClassWriter classWriter, ImVar v) {
        FieldVisitor fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_STATIC, v.getName(), translateType(v.getType()), null, null);
        fieldVisitor.visitEnd();
    }

    private String classDescriptor(ImClass cd) {
        return getPackage(cd).name + "$" + cd.getName();
    }

    private String classDescriptor(ImType t) {
        if (t instanceof ImClassType) {
            ImClassType ct = (ImClassType) t;
            return classDescriptor(ct.getClassDef());
        } else if (t instanceof ImTupleType) {
            ImTupleType tt = (ImTupleType) t;
            return getPackage(tt.attrTrace()).name + "$" + tt.getName();
        } else if (TypesHelper.isStringType(t)) {
            return "java/lang/String";
        } else {
            return "java/lang/Object";
        }
    }

    private String translateType(ImType type) {
        return type.match(new ImType.Matcher<String>() {
            @Override
            public String case_ImTupleType(ImTupleType t) {
                return "L" + classDescriptor(t) + ";";
            }

            @Override
            public String case_ImVoid(ImVoid imVoid) {
                return "V";
            }

            @Override
            public String case_ImClassType(ImClassType c) {
                ImClass cd = c.getClassDef();
                return "L" + classDescriptor(cd) + ";";
            }

            @Override
            public String case_ImArrayTypeMulti(ImArrayTypeMulti a) {
                String t = translateType(a.getEntryType());
                for (Integer i : a.getArraySize()) {
                    t = "[" + t;
                }
                return t;
            }

            @Override
            public String case_ImSimpleType(ImSimpleType s) {
                switch (s.getTypename()) {
                    case "integer":
                        return "I";
                    case "boolean":
                        return "Z";
                    case "string":
                        return "Ljava/lang/String;";
                    case "real":
                        return "F";
                    default:
                        return "L" + s.getTypename() + ";";
                }
            }

            @Override
            public String case_ImArrayType(ImArrayType a) {
                return "[" + translateType(a.getEntryType());
            }

            @Override
            public String case_ImTypeVarRef(ImTypeVarRef imTypeVarRef) {
                return "Ljava/lang/Object;";
            }
        });
    }

    private void translateFunc(ClassWriter classWriter, ImFunction func, int accesss, String name) {
        System.out.println("\n------------------------\ntranslating " + func.getName());
        boolean isAbstract = func.getFlags().contains(IS_ABSTRACT);
        if (isAbstract) {
            accesss |= ACC_ABSTRACT;
        }
        if ((accesss & ACC_ABSTRACT) != 0 && (accesss & ACC_STATIC) != 0) {
            // abstract and static methods
            return;
        }
        String sig = getSignatureDescriptor(func, (accesss & ACC_STATIC) == 0);
        boolean changed = currentClassFunctions.add(func.getName() + sig);
        if (!changed) {
            return;
        }
        MethodVisitor methodVisitor = logMethodVisitor(classWriter.visitMethod(accesss, name, sig, null, null));
        if (!isAbstract) {


            methodVisitor.visitCode();
            Label start = new Label();
            methodVisitor.visitLabel(start);
            methodVisitor.visitLineNumber(line(func), start);

            localVars.clear();
            for (int i = 0; i < func.getParameters().size(); i++) {
                localVars.put(func.getParameters().get(i), i);
            }
            if (func.isNative()) {
                NativeFuncsJvm.generateCode(methodVisitor, func);
            } else {
                for (int i = 0; i < func.getLocals().size(); i++) {
                    ImVar local = func.getLocals().get(i);
                    int index = i + func.getParameters().size();
                    localVars.put(local, index);
                    // initialize array
                    if (local.getType() instanceof ImArrayType) {
                        ImArrayType at = (ImArrayType) local.getType();
                        methodVisitor.visitLdcInsn(Constants.MAX_ARRAY_SIZE);
                        newArrayInstruction(methodVisitor, at.getEntryType());
                        methodVisitor.visitVarInsn(ASTORE, index);
                    }
                }

                translateStatements(methodVisitor, func.getBody());

                if (func.getReturnType() instanceof ImVoid) {
                    methodVisitor.visitInsn(RETURN);
                }
            }

            Label end = new Label();
            localVars.forEach((v, i) -> {
                methodVisitor.visitLocalVariable(v.getName(), translateType(v.getType()), null, start, end, i);
            });
            // TODO do correctly ...
            methodVisitor.visitMaxs(100, 200);
        }
        methodVisitor.visitEnd();

    }

    private MethodVisitor logMethodVisitor(MethodVisitor parent) {
        return new MethodVisitor(Opcodes.ASM5) {
            @Override
            public void visitParameter(String name, int access) {
                println("visitParameter" + Arrays.asList(name, access));
                parent.visitParameter(name, access);
            }

            @Override
            public AnnotationVisitor visitAnnotationDefault() {
                println("visitAnnotationDefault" + Arrays.asList());
                return parent.visitAnnotationDefault();
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                println("visitAnnotation" + Arrays.asList(descriptor, visible));
                return parent.visitAnnotation(descriptor, visible);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                println("visitTypeAnnotation" + Arrays.asList(typeRef, typePath, descriptor, visible));
                return parent.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
            }

            @Override
            public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
                println("visitAnnotableParameterCount" + Arrays.asList(parameterCount, visible));
                parent.visitAnnotableParameterCount(parameterCount, visible);
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                println("visitParameterAnnotation" + Arrays.asList(parameter, descriptor, visible));
                return parent.visitParameterAnnotation(parameter, descriptor, visible);
            }

            @Override
            public void visitAttribute(Attribute attribute) {
                println("visitAttribute" + Arrays.asList(attribute));
                parent.visitAttribute(attribute);
            }

            @Override
            public void visitCode() {
                println("visitCode" + Arrays.asList());
                parent.visitCode();
            }

            @Override
            public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                println("visitFrame" + Arrays.asList(type, numLocal, local, numStack, stack));
                parent.visitFrame(type, numLocal, local, numStack, stack);
            }

            @Override
            public void visitInsn(int opcode) {
                println("visitInsn" + Arrays.asList(opcode));
                parent.visitInsn(opcode);
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                println("visitIntInsn" + Arrays.asList(opcode, operand));
                parent.visitIntInsn(opcode, operand);
            }

            @Override
            public void visitVarInsn(int opcode, int var) {
                println("visitVarInsn" + Arrays.asList(opcode, var));
                parent.visitVarInsn(opcode, var);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                println("visitTypeInsn" + Arrays.asList(opcode, type));
                parent.visitTypeInsn(opcode, type);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                println("visitFieldInsn" + Arrays.asList(opcode, owner, name, descriptor));
                parent.visitFieldInsn(opcode, owner, name, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
                println("visitMethodInsn" + Arrays.asList(opcode, owner, name, descriptor));
                parent.visitMethodInsn(opcode, owner, name, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                println("visitMethodInsn" + Arrays.asList(opcode, owner, name, descriptor, isInterface));
                parent.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                println("visitInvokeDynamicInsn" + Arrays.asList(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments));
                parent.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                println("visitJumpInsn" + Arrays.asList(opcode, label));
                parent.visitJumpInsn(opcode, label);
            }

            @Override
            public void visitLabel(Label label) {
                println("visitLabel" + Arrays.asList(label));
                parent.visitLabel(label);
            }

            @Override
            public void visitLdcInsn(Object value) {
                println("visitLdcInsn" + Arrays.asList(value));
                parent.visitLdcInsn(value);
            }

            @Override
            public void visitIincInsn(int var, int increment) {
                println("visitIincInsn" + Arrays.asList(var, increment));
                parent.visitIincInsn(var, increment);
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                println("visitTableSwitchInsn" + Arrays.asList(min, max, dflt, labels));
                parent.visitTableSwitchInsn(min, max, dflt, labels);
            }

            @Override
            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                println("visitLookupSwitchInsn" + Arrays.asList(dflt, keys, labels));
                parent.visitLookupSwitchInsn(dflt, keys, labels);
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                println("visitMultiANewArrayInsn" + Arrays.asList(descriptor, numDimensions));
                parent.visitMultiANewArrayInsn(descriptor, numDimensions);
            }

            @Override
            public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                println("visitInsnAnnotation" + Arrays.asList(typeRef, typePath, descriptor, visible));
                return parent.visitInsnAnnotation(typeRef, typePath, descriptor, visible);
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                println("visitTryCatchBlock" + Arrays.asList(start, end, handler, type));
                parent.visitTryCatchBlock(start, end, handler, type);
            }

            @Override
            public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                println("visitTryCatchAnnotation" + Arrays.asList(typeRef, typePath, descriptor, visible));
                return parent.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
            }

            @Override
            public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                println("visitLocalVariable" + Arrays.asList(name, descriptor, signature, start, end, index));
                parent.visitLocalVariable(name, descriptor, signature, start, end, index);
            }

            @Override
            public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
                println("visitLocalVariableAnnotation" + Arrays.asList(typeRef, typePath, start, end, index, descriptor, visible));
                return parent.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
            }

            @Override
            public void visitLineNumber(int line, Label start) {
                println("visitLineNumber" + Arrays.asList(line, start));
                parent.visitLineNumber(line, start);
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                println("visitMaxs" + Arrays.asList(maxStack, maxLocals));
                parent.visitMaxs(maxStack, maxLocals);
            }

            @Override
            public void visitEnd() {
                println("visitEnd" + Arrays.asList());
                parent.visitEnd();
            }

            private void println(String s) {

            }
        };
    }

    private void translateStatements(MethodVisitor methodVisitor, ImStmts body) {
        for (ImStmt s : body) {
            if (s instanceof ImNull) {
                continue;
            }
            if (s instanceof ImStatementExpr) {
                ImStatementExpr se = (ImStatementExpr) s;
                if (se.getExpr() instanceof ImNull) {
                    translateStatements(methodVisitor, se.getStatements());
                    continue;
                }
            }
            translateStatement(methodVisitor, s);
            if (s instanceof ImExpr) {
                ImExpr expr = (ImExpr) s;
                if (!(expr.attrTyp() instanceof ImVoid)) {
                    // if the expression produces a result
                    // and is used as a statement, we need to discard the result
                    methodVisitor.visitInsn(POP);
                }
            }
        }
    }

    public void translateExprTyped(MethodVisitor methodVisitor, ImExpr e, ImType expectedType) {
        translateStatement(methodVisitor, e);
        convertType(methodVisitor, e.attrTyp(), expectedType);
    }

    private void convertType(MethodVisitor methodVisitor, ImType from, ImType to) {
        if (from.equalsType(to)) {
            return;
        }
        System.out.println("trying to convert from " + from + " to " + to);
        if (to instanceof ImClassType || to instanceof ImTypeVarRef) {
            if (TypesHelper.isIntType(from)) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            } else if (TypesHelper.isFloatType(from)) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            } else if (TypesHelper.isBoolType(from)) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            } else if (to instanceof ImClassType) {
                // could optimize and only do this if not a subtype:
                methodVisitor.visitTypeInsn(CHECKCAST, classDescriptor(to));
            }
        } else if (from instanceof ImClassType || from instanceof ImTypeVarRef) {
            if (TypesHelper.isIntType(to)) {
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            } else if (TypesHelper.isFloatType(to)) {
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Float");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
            } else if (TypesHelper.isBoolType(to)) {
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            }
        }

    }

    public void translateStatement(MethodVisitor methodVisitor, ImStmt s) {
        System.out.println("translating " + s);
        Label l = new Label();
        methodVisitor.visitLabel(l);
        WPos source = s.attrTrace().attrSource();
        if (!source.isArtificial()) {
            methodVisitor.visitLineNumber(source.getLine(), l);
        }
        s.match(new ImStmt.MatcherVoid() {
            @Override
            public void case_ImTypeVarDispatch(ImTypeVarDispatch imTypeVarDispatch) {
                throw new RuntimeException("TODO " + s);
            }

            @Override
            public void case_ImDealloc(ImDealloc imDealloc) {
                // we have garbage collection in Java so nothing to do
            }

            @Override
            public void case_ImBoolVal(ImBoolVal imBoolVal) {
                if (imBoolVal.getValB()) {
                    methodVisitor.visitInsn(ICONST_1);
                } else {
                    methodVisitor.visitInsn(ICONST_0);
                }
            }

            @Override
            public void case_ImTypeIdOfClass(ImTypeIdOfClass imTypeIdOfClass) {
                throw new RuntimeException("TODO " + s);
            }

            @Override
            public void case_ImVarAccess(ImVarAccess va) {
                ImVar v = va.getVar();
                translateVarAccess(methodVisitor, v);
            }

            @Override
            public void case_ImStringVal(ImStringVal imStringVal) {
                methodVisitor.visitLdcInsn(imStringVal.getValS());
            }

            @Override
            public void case_ImMethodCall(ImMethodCall mc) {
                ImClassType rt = (ImClassType) mc.getReceiver().attrTyp();
                ImClass classDef = rt.getClassDef();
                String className = classDescriptor(classDef);
                translateStatement(methodVisitor, mc.getReceiver());
                int i = 0;
                for (ImExpr a : mc.getArguments()) {
                    ImVar param = mc.getMethod().getImplementation().getParameters().get(i);
                    translateExprTyped(methodVisitor, a, param.getType());
                    i++;
                }
                boolean isInterface = classDef.getFlags().contains(IS_INTERFACE);
                int invokeCommand = isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL;
                methodVisitor.visitMethodInsn(invokeCommand, className, mc.getMethod().getName(), getSignatureDescriptor(mc.getMethod()), isInterface);
                convertType(methodVisitor, mc.getMethod().getImplementation().getReturnType(),
                        mc.attrTyp());
            }

            @Override
            public void case_ImFunctionCall(ImFunctionCall fc) {
                int i = 0;
                for (ImExpr a : fc.getArguments()) {
                    ImVar param = fc.getFunc().getParameters().get(i);
                    translateExprTyped(methodVisitor, a, param.getType());
                    i++;
                }
                String signatureDescriptor = getSignatureDescriptor(fc.getFunc(), false);
                methodVisitor.visitMethodInsn(INVOKESTATIC, getClassName(fc.getFunc()), fc.getFunc().getName(), signatureDescriptor, false);
                convertType(methodVisitor, fc.getFunc().getReturnType(),
                        fc.attrTyp());
            }

            @Override
            public void case_ImRealVal(ImRealVal imRealVal) {
                methodVisitor.visitLdcInsn(new Float(imRealVal.getValR()));
            }

            @Override
            public void case_ImReturn(ImReturn imReturn) {
                ImType returnType = JassIm.ImVoid();
                if (imReturn.getReturnValue() instanceof ImExpr) {
                    ImExpr re = (ImExpr) imReturn.getReturnValue();
                    translateStatement(methodVisitor, re);
                    returnType = re.attrTyp();
                }
                methodVisitor.visitInsn(getReturnInstruction(returnType));
            }

            @Override
            public void case_ImTupleSelection(ImTupleSelection ts) {
                translateStatement(methodVisitor, ts.getTupleExpr());
                int tupleIndex = ts.getTupleIndex();
                readTupleSelectionIndex(ts, tupleIndex);
            }

            private void readTupleSelectionIndex(ImTupleSelection ts, int tupleIndex) {
                Label ifNull = new Label();
                Label join = new Label();
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitJumpInsn(IFNULL, ifNull);
                ImTupleType tt = (ImTupleType) ts.getTupleExpr().attrTyp();
                ImVar p = tt.getParameters().get(tupleIndex);
                methodVisitor.visitFieldInsn(GETFIELD, classDescriptor(tt), p.getName(), translateType(p.getType()));
                methodVisitor.visitJumpInsn(GOTO, join);
                methodVisitor.visitLabel(ifNull);
                methodVisitor.visitInsn(POP);
                pushDefaultValue(ts.attrTyp(), methodVisitor);
                methodVisitor.visitLabel(join);
            }

            @Override
            public void case_ImOperatorCall(ImOperatorCall oc) {
                if (oc.getOp().equals(WurstOperator.AND)) {
                    Label ifTrue = new Label();
                    Label afterAnd = new Label();
                    translateStatement(methodVisitor, oc.getArguments().get(0));
                    methodVisitor.visitJumpInsn(IFNE, ifTrue);
                    methodVisitor.visitInsn(ICONST_0);
                    methodVisitor.visitJumpInsn(GOTO, afterAnd);
                    methodVisitor.visitLabel(ifTrue);
                    translateStatement(methodVisitor, oc.getArguments().get(1));
                    methodVisitor.visitLabel(afterAnd);
                    return;
                } else if (oc.getOp().equals(WurstOperator.OR)) {
                    Label ifFalse = new Label();
                    Label afterOr = new Label();
                    translateStatement(methodVisitor, oc.getArguments().get(0));
                    methodVisitor.visitJumpInsn(IFEQ, ifFalse);
                    methodVisitor.visitInsn(ICONST_1);
                    methodVisitor.visitJumpInsn(GOTO, afterOr);
                    methodVisitor.visitLabel(ifFalse);
                    translateStatement(methodVisitor, oc.getArguments().get(1));
                    methodVisitor.visitLabel(afterOr);
                    return;
                }
                boolean intOperation = isIntOperation(oc);
                boolean floatOperation = isFloatOperation(oc);
                for (ImExpr a : oc.getArguments()) {
                    translateStatement(methodVisitor, a);
                    if (floatOperation && TypesHelper.isIntType(a.attrTyp())) {
                        // convert to float
                        methodVisitor.visitInsn(I2F);
                    }
                }
                switch (oc.getOp()) {
                    case OR:
                        break;
                    case AND:
                        break;
                    case EQ: {
                        if (intOperation) {
                            makeCompare(methodVisitor, IF_ICMPNE, ICONST_1, ICONST_0);
                            return;
                        } else if (floatOperation) {
                            methodVisitor.visitInsn(FCMPL);
                            makeCompare(methodVisitor, IFNE, ICONST_1, ICONST_0);
                            return;
                        } else {
                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                            return;
                        }
                    }
                    case NOTEQ: {
                        if (intOperation) {
                            makeCompare(methodVisitor, IF_ICMPEQ, ICONST_1, ICONST_0);
                            return;
                        } else if (floatOperation) {
                            methodVisitor.visitInsn(FCMPL);
                            makeCompare(methodVisitor, IFEQ, ICONST_1, ICONST_0);
                            return;
                        } else {
                            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                            makeCompare(methodVisitor, IFNE, ICONST_1, ICONST_0);
                            return;
                        }
                    }
                    case LESS_EQ: {
                        if (intOperation) {
                            makeCompare(methodVisitor, IF_ICMPLE, ICONST_0, ICONST_1);
                            return;
                        } else if (floatOperation) {
                            methodVisitor.visitInsn(FCMPL);
                            makeCompare(methodVisitor, IFLE, ICONST_0, ICONST_1);
                            return;
                        }
                        throw new RuntimeException("unhandled case " + oc);
                    }
                    case LESS: {
                        if (intOperation) {
                            makeCompare(methodVisitor, IF_ICMPLT, ICONST_0, ICONST_1);
                            return;
                        } else if (floatOperation) {
                            methodVisitor.visitInsn(FCMPL);
                            makeCompare(methodVisitor, IFLE, ICONST_0, ICONST_1);
                            return;
                        }
                        throw new RuntimeException("unhandled case " + oc);
                    }
                    case GREATER_EQ: {
                        if (intOperation) {
                            makeCompare(methodVisitor, IF_ICMPGE, ICONST_0, ICONST_1);
                            return;
                        } else if (floatOperation) {
                            methodVisitor.visitInsn(FCMPL);
                            makeCompare(methodVisitor, IFGE, ICONST_0, ICONST_1);
                            return;
                        }
                        throw new RuntimeException("unhandled case " + oc);
                    }
                    case GREATER: {
                        if (intOperation) {
                            makeCompare(methodVisitor, IF_ICMPGT, ICONST_0, ICONST_1);
                            return;
                        } else if (floatOperation) {
                            methodVisitor.visitInsn(FCMPL);
                            makeCompare(methodVisitor, IFGT, ICONST_0, ICONST_1);
                            return;
                        }
                        throw new RuntimeException("unhandled case " + oc);
                    }
                    case PLUS:
                        if (intOperation) {
                            methodVisitor.visitInsn(IADD);
                            return;
                        } else if (floatOperation) {
                            methodVisitor.visitInsn(FADD);
                            return;
                        } else if (isStringOperation(oc)) {
                            methodVisitor.visitMethodInsn(INVOKESTATIC, wurstMain.name, "stringAppend", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                            return;
                        }
                        throw new RuntimeException("unhandled case " + oc);
                    case MINUS:
                        if (intOperation) {
                            methodVisitor.visitInsn(ISUB);
                            return;
                        } else if (floatOperation) {
                            methodVisitor.visitInsn(FSUB);
                            return;
                        }
                        throw new RuntimeException("unhandled case " + oc);
                    case MULT:
                        if (intOperation) {
                            methodVisitor.visitInsn(IMUL);
                            return;
                        } else if (floatOperation) {
                            methodVisitor.visitInsn(FMUL);
                            return;
                        }
                        throw new RuntimeException("unhandled case " + oc);
                    case DIV_REAL:
                        methodVisitor.visitInsn(FDIV);
                        return;
                    case DIV_INT:
                        methodVisitor.visitInsn(IDIV);
                        return;
                    case MOD_REAL:
                        methodVisitor.visitInsn(FREM);
                        return;
                    case MOD_INT:
                        methodVisitor.visitInsn(IREM);
                        return;
                    case NOT:
                        methodVisitor.visitInsn(ICONST_1);
                        methodVisitor.visitInsn(IXOR);
                        return;
                    case UNARY_MINUS:
                        if (intOperation) {
                            methodVisitor.visitInsn(INEG);
                            return;
                        } else if (floatOperation) {
                            methodVisitor.visitInsn(FNEG);
                            return;
                        }
                        throw new RuntimeException("TODO " + s + " " + oc.getArguments().get(0).attrTyp());
                }
                throw new RuntimeException("TODO " + s);
            }

            @Override
            public void case_ImVarArrayAccess(ImVarArrayAccess e) {
                ImVar v = e.getVar();
                ImArrayType arrayType = (ImArrayType) v.getType();
                translateVarAccess(methodVisitor, v);
                for (int i = 0; i < e.getIndexes().size() - 1; i++) {
                    ImExpr index = e.getIndexes().get(i);
                    translateStatement(methodVisitor, index);
                    methodVisitor.visitInsn(AALOAD);
                }
                translateStatement(methodVisitor, Utils.getLast(e.getIndexes()));
                methodVisitor.visitInsn(getArrayLoadInstruction(arrayType.getEntryType()));
            }

            @Override
            public void case_ImAlloc(ImAlloc imAlloc) {
                ImClass cd = imAlloc.getClazz().getClassDef();
                String className = className(cd, getPackage(cd));
                methodVisitor.visitTypeInsn(NEW, className);
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, className, "<init>", "()V", false);
            }

            @Override
            public void case_ImIntVal(ImIntVal imIntVal) {
                methodVisitor.visitLdcInsn(imIntVal.getValI());
            }

            @Override
            public void case_ImExitwhen(ImExitwhen e) {
                translateStatement(methodVisitor, e.getCondition());
                methodVisitor.visitJumpInsn(IFNE, currentEndLoopLabel);
            }

            @Override
            public void case_ImVarargLoop(ImVarargLoop imVarargLoop) {
                throw new RuntimeException("TODO " + s);
            }

            @Override
            public void case_ImNull(ImNull imNull) {
                methodVisitor.visitInsn(ACONST_NULL);
            }

            @Override
            public void case_ImLoop(ImLoop imLoop) {
                Label beginLoop = new Label();
                Label endLoop = new Label();
                methodVisitor.visitLabel(beginLoop);
                JvmTranslation.currentEndLoopLabel = endLoop;
                translateStatements(methodVisitor, imLoop.getBody());
                methodVisitor.visitJumpInsn(GOTO, beginLoop);
                methodVisitor.visitLabel(endLoop);
            }

            @Override
            public void case_ImMemberAccess(ImMemberAccess ma) {
                ImVar var = ma.getVar();
                translateStatement(methodVisitor, ma.getReceiver());
                methodVisitor.visitFieldInsn(GETFIELD, getClassName(var), var.getName(), translateType(var.getType()));
                ImType t = var.getType();
                for (ImExpr index : ma.getIndexes()) {
                    translateStatement(methodVisitor, index);
                    t = arrayEntryType(t);
                    methodVisitor.visitInsn(getArrayLoadInstruction(t));
                }
                convertType(methodVisitor, ma.attrTypRaw(), ma.attrTyp());
            }

            @Override
            public void case_ImGetStackTrace(ImGetStackTrace imGetStackTrace) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, wurstMain.name, "getStackTrace", "()Ljava/lang/String;", false);
            }

            @Override
            public void case_ImTupleExpr(ImTupleExpr te) {
                ImTupleType tt = te.getTupleType();
                String classDescriptor = classDescriptor(tt);


                methodVisitor.visitTypeInsn(NEW, classDescriptor);
                methodVisitor.visitInsn(DUP);
                for (ImExpr arg : te.getExprs()) {
                    translateStatement(methodVisitor, arg);
                }
                methodVisitor.visitMethodInsn(INVOKESPECIAL, classDescriptor, "<init>", getTupleConstructorSignature(tt), false);
            }

            @Override
            public void case_ImTypeIdOfObj(ImTypeIdOfObj imTypeIdOfObj) {
                throw new RuntimeException("TODO " + s);
            }

            @Override
            public void case_ImSet(ImSet imSet) {
                ImLExpr left = imSet.getLeft();
                ImExpr right = imSet.getRight();
                Runnable translateRight = () -> {
                    translateStatement(methodVisitor, right);
                    convertType(methodVisitor, right.attrTyp(), left.attrTypRaw());
                };
                translateAssignment(left, translateRight);
            }

            private void translateAssignment(ImLExpr left, Runnable translateRight) {
                left.match(new ImLExpr.MatcherVoid() {

                    @Override
                    public void case_ImTupleSelection(ImTupleSelection ts) {
                        // TODO extract side effects from tupleExpr, so they get only executed once
                        ImLExpr tupleExpr = (ImLExpr) ts.getTupleExpr();
                        ImTupleType tt = (ImTupleType) tupleExpr.attrTyp();
                        translateAssignment(tupleExpr, () -> {
                            // construct a new tuple and use it as the right hand side
                            String classDescriptor = classDescriptor(tt);
                            methodVisitor.visitTypeInsn(NEW, classDescriptor);
                            methodVisitor.visitInsn(DUP);
                            // arguments unchanged, except for updated field
                            for (int i = 0; i < tt.getParameters().size(); i++) {
                                if (i == ts.getTupleIndex()) {
                                    translateRight.run();
                                } else {
                                    translateStatement(methodVisitor, tupleExpr);
                                    readTupleSelectionIndex(ts, i);
                                }
                            }
                            methodVisitor.visitMethodInsn(INVOKESPECIAL, classDescriptor, "<init>", getTupleConstructorSignature(tt), false);

                        });


//
//                        translateStatement(methodVisitor, ts.getTupleExpr());
//                        ImTupleType tt = (ImTupleType) ts.getTupleExpr().attrTyp();
//                        ImVar p = tt.getParameters().get(ts.getTupleIndex());
//                        translateStatement(methodVisitor, right);
//                        // TODO this is actually more difficult: need to copy the tuple
//                        methodVisitor.visitFieldInsn(PUTFIELD, classDescriptor(tt), p.getName(), translateType(p.getType()));
                    }

                    @Override
                    public void case_ImVarAccess(ImVarAccess leftVar) {
                        translateRight.run();
                        ImVar var = leftVar.getVar();
                        if (localVars.containsKey(var)) {
                            int varIndex = localVars.get(var);
                            methodVisitor.visitVarInsn(getStoreInstruction(var.getType()), varIndex);
                        } else {
                            if (var.getParent().getParent() instanceof ImClass) {
                                // must be a field:
                                // load 'this', which is always in index 0
                                methodVisitor.visitVarInsn(getLoadInstruction(var.getType()), 0);
                                methodVisitor.visitFieldInsn(PUTFIELD, getClassName(var), var.getName(), translateType(var.getType()));
                            } else {
                                // otherwise it is a global variable
                                methodVisitor.visitFieldInsn(PUTSTATIC, getClassName(var), var.getName(), translateType(var.getType()));
                            }
                        }
                    }

                    @Override
                    public void case_ImVarArrayAccess(ImVarArrayAccess e) {
                        ImVar v = e.getVar();
                        ImArrayType arrayType = (ImArrayType) v.getType();
                        translateVarAccess(methodVisitor, v);
                        translateArrayWrite(arrayType.getEntryType(), e.getIndexes());

                    }

                    private void translateArrayWrite(ImType entryType, ImExprs indexes) {
                        for (int i = 0; i < indexes.size() - 1; i++) {
                            ImExpr index = indexes.get(i);
                            translateStatement(methodVisitor, index);
                            methodVisitor.visitInsn(AALOAD);
                        }
                        translateStatement(methodVisitor, Utils.getLast(indexes));
                        translateRight.run();
                        methodVisitor.visitInsn(getArrayStoreInstruction(entryType));
                    }

                    @Override
                    public void case_ImMemberAccess(ImMemberAccess e) {
                        translateStatement(methodVisitor, e.getReceiver());
                        if (e.getIndexes().isEmpty()) {
                            translateRight.run();
                            methodVisitor.visitFieldInsn(PUTFIELD, getClassName(e.getVar()), e.getVar().getName(), translateType(e.getVar().getType()));
                        } else {
                            methodVisitor.visitFieldInsn(GETFIELD, getClassName(e.getVar()), e.getVar().getName(), translateType(e.getVar().getType()));
                            translateArrayWrite(((ImArrayTypeMulti) e.getVar().getType()).getEntryType(), e.getIndexes());
                        }
                    }

                    @Override
                    public void case_ImStatementExpr(ImStatementExpr imStatementExpr) {
                        throw new RuntimeException("TODO " + s);
                    }

                    @Override
                    public void case_ImTupleExpr(ImTupleExpr imTupleExpr) {
                        throw new RuntimeException("TODO " + s);
                    }
                });
            }

            @Override
            public void case_ImStatementExpr(ImStatementExpr se) {
                translateStatements(methodVisitor, se.getStatements());
                translateStatement(methodVisitor, se.getExpr());
            }

            @Override
            public void case_ImCompiletimeExpr(ImCompiletimeExpr imCompiletimeExpr) {
                throw new RuntimeException("TODO " + s);
            }

            @Override
            public void case_ImIf(ImIf imIf) {
                translateStatement(methodVisitor, imIf.getCondition());
                Label elseLabel = new Label();
                Label endifLabel = new Label();
                methodVisitor.visitJumpInsn(IFEQ, elseLabel);
                translateStatements(methodVisitor, imIf.getThenBlock());
                methodVisitor.visitJumpInsn(GOTO, endifLabel);
                methodVisitor.visitLabel(elseLabel);
                translateStatements(methodVisitor, imIf.getElseBlock());
                methodVisitor.visitLabel(endifLabel);
            }

            @Override
            public void case_ImCast(ImCast imCast) {
                translateStatement(methodVisitor, imCast.getExpr());
                ImType et = imCast.getExpr().attrTyp();
                if (imCast.getToType().equalsType(imInt())) {
                    if (et instanceof ImClassType) {
                        methodVisitor.visitMethodInsn(INVOKESTATIC, wurstMain.name, "castToIndex", "(Ljava/lang/Object;)I", false);
                    } else if (TypesHelper.isIntType(et)) {
                        // already int
                    } else {
                        throw new RuntimeException("TODO " + s);
                    }
                } else if (imCast.getToType() instanceof ImClassType
                        && et.equalsType(imInt())) {
                    methodVisitor.visitMethodInsn(INVOKESTATIC, wurstMain.name, "castFromIndex", "(Ljava/lang/Object;)I", false);
                    methodVisitor.visitTypeInsn(CHECKCAST, classDescriptor(imCast.getToType()));
                } else if (imCast.getToType() instanceof ImClassType) {
                    methodVisitor.visitTypeInsn(CHECKCAST, classDescriptor(imCast.getToType()));
                } else {
                    throw new RuntimeException("TODO " + s);
                }
            }

            @Override
            public void case_ImFuncRef(ImFuncRef fr) {
                // TODO choose suitable representation for function refs
                // Could be a lambda or a reflection method
                methodVisitor.visitInsn(NULL);
            }

            @Override
            public void case_ImInstanceof(ImInstanceof e) {
                translateStatement(methodVisitor, e.getObj());
                methodVisitor.visitTypeInsn(INSTANCEOF, classDescriptor(e.getClazz().getClassDef()));
            }
        });
    }

    private ImType arrayEntryType(ImType t) {
        if (t instanceof ImArrayType) {
            return ((ImArrayType) t).getEntryType();
        } else if (t instanceof ImArrayTypeMulti) {
            ImArrayTypeMulti at = (ImArrayTypeMulti) t;
            if (at.getArraySize().size() <= 1) {
                return at.getEntryType();
            } else {
                return JassIm.ImArrayTypeMulti(at.getEntryType(), at.getArraySize().stream().skip(1).collect(Collectors.toList()));
            }
        }
        throw new RuntimeException("unhandled: " + t);
    }

    private void translateVarAccess(MethodVisitor methodVisitor, ImVar v) {
        if (localVars.containsKey(v)) {
            int index = localVars.get(v);
            methodVisitor.visitVarInsn(getLoadInstruction(v.getType()), index);
        } else {
            ImVar var = v;
            if (var.getParent().getParent() instanceof ImClass) {
                // must be a field:
                // load 'this', which is always in index 0
                methodVisitor.visitVarInsn(getLoadInstruction(v.getType()), 0);
                methodVisitor.visitFieldInsn(GETFIELD, getClassName(var), var.getName(), translateType(var.getType()));
            } else {
                // otherwise it is a global variable
                methodVisitor.visitFieldInsn(GETSTATIC, getClassName(var), var.getName(), translateType(var.getType()));
            }
        }
    }


    public static int getReturnInstruction(ImType returnType) {
        return returnType.match(new ImType.Matcher<Integer>() {
            @Override
            public Integer case_ImTupleType(ImTupleType imTupleType) {
                return (ARETURN);
            }

            @Override
            public Integer case_ImVoid(ImVoid imVoid) {
                return (RETURN);
            }

            @Override
            public Integer case_ImClassType(ImClassType imClassType) {
                return (ARETURN);
            }

            @Override
            public Integer case_ImArrayTypeMulti(ImArrayTypeMulti imArrayTypeMulti) {
                return (ARETURN);
            }

            @Override
            public Integer case_ImSimpleType(ImSimpleType t) {
                int returnIns = ARETURN;
                switch (t.getTypename()) {
                    case "integer":
                    case "boolean":
                        returnIns = IRETURN;
                        break;
                    case "real":
                        returnIns = FRETURN;
                        break;

                }
                return (returnIns);
            }

            @Override
            public Integer case_ImArrayType(ImArrayType imArrayType) {
                throw new RuntimeException("TODO");
            }

            @Override
            public Integer case_ImTypeVarRef(ImTypeVarRef imTypeVarRef) {
                return ARETURN;
            }
        });
    }

    public static void pushDefaultValue(ImType t, MethodVisitor methodVisitor) {
        t.match(new ImType.MatcherVoid() {
            @Override
            public void case_ImTupleType(ImTupleType imTupleType) {
                methodVisitor.visitInsn(ACONST_NULL);
            }

            @Override
            public void case_ImVoid(ImVoid imVoid) {
            }

            @Override
            public void case_ImClassType(ImClassType imClassType) {
                methodVisitor.visitInsn(ACONST_NULL);
            }

            @Override
            public void case_ImArrayTypeMulti(ImArrayTypeMulti imArrayTypeMulti) {
                methodVisitor.visitInsn(ACONST_NULL);
            }

            @Override
            public void case_ImSimpleType(ImSimpleType t) {
                switch (t.getTypename()) {
                    case "integer":
                    case "boolean":
                        methodVisitor.visitInsn(ICONST_0);
                        break;
                    case "real":
                        methodVisitor.visitInsn(FCONST_0);
                        break;
                    default:
                        methodVisitor.visitInsn(ACONST_NULL);
                }
            }

            @Override
            public void case_ImArrayType(ImArrayType imArrayType) {
                methodVisitor.visitInsn(ACONST_NULL);
            }

            @Override
            public void case_ImTypeVarRef(ImTypeVarRef imTypeVarRef) {
                methodVisitor.visitInsn(ACONST_NULL);
            }
        });
    }

    private String className(ImClass cd, JPackage aPackage) {
        return aPackage.name + "$" + cd.getName();
    }

    private String className(ImTupleType cd, JPackage aPackage) {
        return aPackage.name + "$" + cd.getName();
    }

    private String getClassName(ImVar var) {
        ImClass elemClass = getElemClass(var);
        if (elemClass == null) {
            return getPackage(var).name;
        }
        return getPackage(var).name + "$" + elemClass.getName();
    }

    private String getClassName(ImFunction f) {
        ImClass elemClass = getElemClass(f);
        if (elemClass == null) {
            return getPackage(f).name;
        }
        return getPackage(f).name + "$" + elemClass.getName();
    }

    private boolean isIntOperation(ImOperatorCall oc) {
        for (ImExpr a : oc.getArguments()) {
            if (!TypesHelper.isIntType(a.attrTyp())) {
                return false;
            }
        }
        return true;
    }

    private boolean isFloatOperation(ImOperatorCall oc) {
        for (ImExpr a : oc.getArguments()) {
            if (TypesHelper.isFloatType(a.attrTyp())) {
                return true;
            }
        }
        return false;
    }

    private boolean isStringOperation(ImOperatorCall oc) {
        for (ImExpr a : oc.getArguments()) {
            if (TypesHelper.isStringType(a.attrTyp())) {
                return true;
            }
        }
        return false;
    }

    private void makeCompare(MethodVisitor methodVisitor, int jumpInstruction, int ifFalse, int ifTrue) {
        Label equal = new Label();
        Label afterCompare = new Label();
        methodVisitor.visitJumpInsn(jumpInstruction, equal);
        methodVisitor.visitInsn(ifFalse);
        methodVisitor.visitJumpInsn(GOTO, afterCompare);
        methodVisitor.visitLabel(equal);
        methodVisitor.visitInsn(ifTrue);
        methodVisitor.visitLabel(afterCompare);
    }


    private int getLoadInstruction(ImType type) {
        return type.match(new ImType.Matcher<Integer>() {
            @Override
            public Integer case_ImTupleType(ImTupleType imTupleType) {
                return ALOAD;
            }

            @Override
            public Integer case_ImVoid(ImVoid imVoid) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImClassType(ImClassType imClassType) {
                return ALOAD;
            }

            @Override
            public Integer case_ImArrayTypeMulti(ImArrayTypeMulti imArrayTypeMulti) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImSimpleType(ImSimpleType t) {
                switch (t.getTypename()) {
                    case "integer":
                        return ILOAD;
                    case "real":
                        return FLOAD;
                    case "boolean":
                        return ILOAD;
                    default:
                        return ALOAD;
                }
            }

            @Override
            public Integer case_ImArrayType(ImArrayType imArrayType) {
                return ALOAD;
            }

            @Override
            public Integer case_ImTypeVarRef(ImTypeVarRef imTypeVarRef) {
                return ALOAD;
            }
        });
    }

    private int getStoreInstruction(ImType type) {
        return type.match(new ImType.Matcher<Integer>() {
            @Override
            public Integer case_ImTupleType(ImTupleType imTupleType) {
                return ASTORE;
            }

            @Override
            public Integer case_ImVoid(ImVoid imVoid) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImClassType(ImClassType imClassType) {
                return ASTORE;
            }

            @Override
            public Integer case_ImArrayTypeMulti(ImArrayTypeMulti imArrayTypeMulti) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImSimpleType(ImSimpleType t) {
                switch (t.getTypename()) {
                    case "integer":
                        return ISTORE;
                    case "real":
                        return FSTORE;
                    case "boolean":
                        return ISTORE;
                    default:
                        return ASTORE;
                }
            }

            @Override
            public Integer case_ImArrayType(ImArrayType imArrayType) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImTypeVarRef(ImTypeVarRef imTypeVarRef) {
                throw new RuntimeException("TODO " + type);
            }
        });
    }

    private int getArrayStoreInstruction(ImType type) {
        return type.match(new ImType.Matcher<Integer>() {
            @Override
            public Integer case_ImTupleType(ImTupleType imTupleType) {
                return AASTORE;
            }

            @Override
            public Integer case_ImVoid(ImVoid imVoid) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImClassType(ImClassType imClassType) {
                return AASTORE;
            }

            @Override
            public Integer case_ImArrayTypeMulti(ImArrayTypeMulti imArrayTypeMulti) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImSimpleType(ImSimpleType t) {
                switch (t.getTypename()) {
                    case "integer":
                        return IASTORE;
                    case "real":
                        return FASTORE;
                    default:
                        return AASTORE;
                }
            }

            @Override
            public Integer case_ImArrayType(ImArrayType imArrayType) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImTypeVarRef(ImTypeVarRef imTypeVarRef) {
                throw new RuntimeException("TODO " + type);
            }
        });
    }

    private int getArrayLoadInstruction(ImType type) {
        return type.match(new ImType.Matcher<Integer>() {
            @Override
            public Integer case_ImTupleType(ImTupleType imTupleType) {
                return AALOAD;
            }

            @Override
            public Integer case_ImVoid(ImVoid imVoid) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImClassType(ImClassType imClassType) {
                return AALOAD;
            }

            @Override
            public Integer case_ImArrayTypeMulti(ImArrayTypeMulti imArrayTypeMulti) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImSimpleType(ImSimpleType t) {
                switch (t.getTypename()) {
                    case "integer":
                        return IALOAD;
                    case "real":
                        return FALOAD;
                    default:
                        return AALOAD;
                }
            }

            @Override
            public Integer case_ImArrayType(ImArrayType imArrayType) {
                throw new RuntimeException("TODO " + type);
            }

            @Override
            public Integer case_ImTypeVarRef(ImTypeVarRef imTypeVarRef) {
                throw new RuntimeException("TODO " + type);
            }
        });
    }


    private int line(de.peeeq.wurstscript.jassIm.Element e) {
        return e.attrTrace().attrSource().getLine();
    }

    @NotNull
    private String getSignatureDescriptor(ImFunction func, boolean skipFirstArg) {
        StringBuilder sb = new StringBuilder("(");
        if (func.getName().equals("main")) {
            // add args parameter:
            sb.append("[Ljava/lang/String;");
        }
        for (ImVar v : func.getParameters()) {
            if (skipFirstArg) {
                skipFirstArg = false;
                continue;
            }
            sb.append(translateType(v.getType()));
        }
        sb.append(")");
        sb.append(translateType(func.getReturnType()));
        return sb.toString();
    }

    private String getSignatureDescriptor(List<ImType> paramTypes, ImType resultType) {
        StringBuilder sb = new StringBuilder("(");
        for (ImType t : paramTypes) {
            sb.append(translateType(t));
        }
        sb.append(")");
        sb.append(translateType(resultType));
        return sb.toString();
    }

    @NotNull
    private String getSignatureDescriptor(ImMethod func) {
        return getSignatureDescriptor(func.getImplementation(), true);
    }


}
