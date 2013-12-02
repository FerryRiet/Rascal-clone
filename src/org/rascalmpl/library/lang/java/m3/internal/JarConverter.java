package org.rascalmpl.library.lang.java.m3.internal;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.type.TypeStore;
import org.eclipse.jdt.core.Signature;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.rascalmpl.interpreter.IEvaluatorContext;

public class JarConverter extends M3Converter {
        private String jarFile;
        private String ClassFile;
        private String LogPath;
        private String classScheme;

        JarConverter(TypeStore typeStore) {
                super(typeStore);
        }

        private String extractJarName(ISourceLocation jarLoc) {
                String tmp = jarLoc.getPath().substring(0,
                                jarLoc.getPath().indexOf("!"));
                return tmp.substring(tmp.lastIndexOf("/") + 1);
        }

        private String extractClassName(ISourceLocation jarLoc) {
                return jarLoc.getPath().substring(jarLoc.getPath().indexOf("!") + 1);
        }

        @SuppressWarnings("unchecked")
		public void convert(ISourceLocation jarLoc, IEvaluatorContext ctx) {

                this.jarFile = extractJarName(jarLoc);
                this.ClassFile = extractClassName(jarLoc);
                this.LogPath = this.ClassFile.replace(".class", "");
                if(this.LogPath.contains("$")){ this.LogPath = LogPath.substring(0, LogPath.indexOf("$"));}
                this.classScheme = "java+class";
                if((cn.access & Opcodes.ACC_INTERFACE) != 0) classScheme = "java+interface";
                try {
                        ClassReader cr = new ClassReader(ctx.getResolverRegistry()
                                        .getInputStream(jarLoc.getURI()));
                        ClassNode cn = new ClassNode();

                        cr.accept(cn, ClassReader.SKIP_DEBUG);

                        this.insert(
                                        this.declarations,
                                        values.sourceLocation(classScheme, "",  "/"
                                                        + cn.name), values.sourceLocation(jarFile + "!" + ClassFile));

                        this.insert(
                                        this.extendsRelations,
                                        values.sourceLocation(classScheme, "",  "/"
                                                        + cn.name),
                                        values.sourceLocation(classScheme, "", cn.superName));

                        for ( int fs = 0 ; fs < 15 ; fs++ ) { 
                            if ( (cn.access & (0x0001 << fs )) != 0 ) {
                                    this.insert(this.modifiers,values.sourceLocation(classScheme, "",  "/"
                                            + cn.name),mapFieldAccesCode(0x0001<<fs) );                                
                            }
                        }
                        
                        // @implements={<|java+class:///m3startv2/viaInterface|,|java+interface:///m3startv2/m3Interface|>},
                        for (int i = 0; i < cn.interfaces.size(); ++i) {
                                String iface = (String) cn.interfaces.get(i);
                                this.insert(
                                                this.implementsRelations,
                                                values.sourceLocation(classScheme, "", "/"
                                                                + cn.name),
                                                values.sourceLocation("java+interface", ClassFile, "/"
                                                                + iface));
                        }

                        // TODO: output class modifiers
                        // cn.access check original M3 model 
                        
                        
                        emitMethods(cn.methods);
                        emitFields(cn.fields);

                } catch (IOException e) {
                        e.printStackTrace();
                } catch (URISyntaxException e) {
                        throw new RuntimeException("Should not happen", e);
                }
        }

        private void emitMethods(List<MethodNode> methods) {
                try {
                        for (int i = 0; i < methods.size(); ++i) {
                                MethodNode method = methods.get(i);
                                System.out.println(new String("Signature :") + method.name
                                                + " " + method.signature + "  " + method.desc);
                                
                                if(method.name.contains("<")){
                                        String name = LogPath.substring(LogPath.lastIndexOf("/"));
                                        insertDeclMethod("java+constructor",method.signature,method.desc,name,method.access);
                                }else{
                                        insertDeclMethod("java+method",method.signature,method.desc,method.name,method.access);                                        
                                }
                        }

                } catch (Exception e) {
                        e.printStackTrace();
                }
        }

        private void insertDeclMethod(String type, String signature, String desc, String name,int access) throws URISyntaxException{
                String sig ;
                if( signature != null) {
                        sig = extractSignature(signature);
                }else {
                        sig = extractSignature(desc);
                }        
                this.insert(this.declarations,values.sourceLocation(type, "", LogPath + "/" + name + "(" + sig + ")"),values.sourceLocation(jarFile + "!" + ClassFile));        
                for ( int fs = 0 ; fs < 15 ; fs++ ) { 
                        if ( (access & (0x0001 << fs )) != 0 ) {
                                this.insert(this.modifiers,values.sourceLocation(type, "" ,LogPath + "/" + name + "(" + sig + ")"),mapFieldAccesCode(0x0001<<fs) );                                
                        }
                }
                // Containment of methods.
                this.insert(this.containment,values.sourceLocation(classScheme, "", LogPath ), values.sourceLocation(type, "", LogPath + "/" + name + "(" + sig + ")"));        
        }
        
        private String extractSignature(String sig){
                String args = Signature.toString(sig);
                args = args.substring(args.indexOf("(")+1,args.indexOf(")"));
                args = args.replaceAll("\\s+","");
                args = args.replaceAll("/",".");
                return args;                
        }
        
        private IConstructor mapFieldAccesCode(int code) {
                // Check the original M3 implementation for possible IConstructor types.
                switch (code) {
                case Opcodes.ACC_PUBLIC:
                        return constructModifierNode("public");
                case Opcodes.ACC_PRIVATE:
                        return constructModifierNode("private");
                case Opcodes.ACC_PROTECTED:
                        return constructModifierNode("protected");
                case Opcodes.ACC_STATIC:
                        return constructModifierNode("static");
                case Opcodes.ACC_FINAL:
                    	return constructModifierNode("final");
                case Opcodes.ACC_SYNCHRONIZED:
                    	return constructModifierNode("synchronized");
                case Opcodes.ACC_ABSTRACT:
            			return constructModifierNode("abstract");
                case Opcodes.ACC_VOLATILE:
            			return constructModifierNode("volatile");
                case Opcodes.ACC_TRANSIENT:
            			return constructModifierNode("transient");
                case Opcodes.ACC_NATIVE:
            			return constructModifierNode("native");
                		
                
                // TODO: GIT PULL/MERGE  ORIGINAL RASCAL VERSION > 2013-11-30 (PaulKlint commit)  
                //case Opcodes.ACC_DEPRECATED:
                //		return constructModifierNode("deprecated");
                    
                        
                default:
                        return constructModifierNode("public");
                }
        }

        // <|java+field:///m3startv2/Main/intField|,|project://m3startv2/src/m3startv2/Main.java|(54,13,<5,12>,<5,25>)>,
        private void emitFields(List<FieldNode> fields) {
                try {
                        for (int i = 0; i < fields.size(); ++i) {
                                FieldNode field = fields.get(i);
                                //System.out.println("Debug......." + field.name);
                                this.insert(this.declarations,values.sourceLocation("java+field","" , LogPath+ "/"+ field.name), values.sourceLocation(jarFile + "!" + ClassFile));

                                // Containment of fields. 
                                this.insert(this.containment,values.sourceLocation(classScheme, "", LogPath ), values.sourceLocation("java+field","" , LogPath+ "/"+ field.name));        

                                // The jvm acces codes specify 15 different modifiers (more then in the Java language itself)
                                for ( int fs = 0 ; fs < 15 ; fs++ ) { 
                                        if ( (field.access & (0x0001 << fs )) != 0 ) {
                                                this.insert(this.modifiers,values.sourceLocation("java+field", "", LogPath + "/" + field.name),mapFieldAccesCode(1<<fs) );
                                        }
                                }
                        }
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }
        
        @SuppressWarnings("unused")
		private class SigVisitor extends SignatureVisitor{

                public SigVisitor(int api) {
                        super(api);
                        // TODO Auto-generated constructor stub
                }
                
                public void visitFormalTypeParameter(String name){
                        try {
                                System.out.println(name);
                                JarConverter.this.insert(JarConverter.this.declarations,values.sourceLocation("java+typeVariable","",LogPath + "/" + name),values.sourceLocation(jarFile + "!" + ClassFile) );
                        } catch (URISyntaxException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        }
                }
                
                public void visitBaseType(char descriptor){
                        System.out.println(descriptor);
                }
                
        }
}
