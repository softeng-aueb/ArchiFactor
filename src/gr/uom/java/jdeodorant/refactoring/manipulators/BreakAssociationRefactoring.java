package gr.uom.java.jdeodorant.refactoring.manipulators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringContribution;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.eclipse.ui.PartInitException;

import gr.aueb.java.jpa.AssociationObject;
import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.jdeodorant.refactoring.views.MyRefactoringWizard;

public class BreakAssociationRefactoring {

	AssociationObject association;
	String FKfieldName;
	String FKtype;
	String OwnedExtractedFieldName;
	String OwnedExtractedFieldType;
	boolean OwnedExtractedFieldIsSet;
	String OwnedServiceClassName;
	
	String OwnerExtractedFieldName;
	String OwnerExtractedFieldType;
	boolean OwnerExtractedFieldIsSet;
	String OwnerServiceClassName;
	
	List<ClassObject> classes = new ArrayList<ClassObject>();
	
	private Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges = new LinkedHashMap<ICompilationUnit, CompilationUnitChange>();
	
	public BreakAssociationRefactoring(AssociationObject association,List<ClassObject> classes) {
		this.association = association;
		this.classes = classes;
		
		FieldObject OwnedFieldObj = association.getOwnedClass().getAssociatedObjectByClass(association.getOwnerClass().getClassObject());
		//System.out.println(fielsObj.getType().toString()+" "+fielsObj.getName());
		this.OwnedExtractedFieldName = OwnedFieldObj.getName();
		if(OwnedFieldObj.getType().getGenericType()!=null){
			String[] fullFieldName = OwnedFieldObj.getType().getGenericType().toString().split("\\.");
			this.OwnedExtractedFieldType = fullFieldName[fullFieldName.length-1];
			this.OwnedExtractedFieldType = this.OwnedExtractedFieldType.substring(0, this.OwnedExtractedFieldType.length() - 1);
			OwnedExtractedFieldIsSet = true;
		}else {
			String[] fullFieldName = OwnedFieldObj.getType().toString().split("\\.");
			this.OwnedExtractedFieldType = fullFieldName[fullFieldName.length-1];
			OwnedExtractedFieldIsSet = false;
		}
		
		FieldObject OwnerfieldObj = association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject());
		this.OwnerExtractedFieldName = OwnerfieldObj.getName();
		if(OwnerfieldObj.getType().getGenericType()!=null){
			String[] fullFieldName = OwnerfieldObj.getType().getGenericType().toString().split("\\.");
			this.OwnerExtractedFieldType = fullFieldName[fullFieldName.length-1];
			this.OwnerExtractedFieldType = this.OwnerExtractedFieldType.substring(0, this.OwnerExtractedFieldType.length() - 1);
			this.OwnerExtractedFieldIsSet = true;
		}else {
			String[] fullFieldName = OwnerfieldObj.getType().toString().split("\\.");
			this.OwnerExtractedFieldType = fullFieldName[fullFieldName.length-1];
			this.OwnerExtractedFieldIsSet = false;
		}
		
		String[] typeName = association.getOwnedClass().getIdField().getType().toString().split("\\.");
        this.FKtype=typeName[typeName.length-1];
        
        org.eclipse.jdt.core.dom.Annotation joinColumnAnnotation = association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject()).getAnnotations().get(1);
        String s = new StringBuilder().append('"').toString();
        String[] arr = joinColumnAnnotation.toString().split(s);
        this.FKfieldName = arr[1];
        
        String[] array = association.getOwnerClass().getClassObject().getName().split("\\.");
        this.OwnedServiceClassName = array[array.length-1]+"Service";
        
        String[] array2 = association.getOwnedClass().getClassObject().getName().split("\\.");
        this.OwnerServiceClassName = array2[array2.length-1]+"Service";
	}
	
	public IJavaProject apply() {
		ICompilationUnit cu = (ICompilationUnit)association.getOwnedClass().getClassObject().getITypeRoot().getPrimaryElement();
		Set<MethodDeclaration> extractedMethods = association.getOwnedClass().getMethodDeclarationsByField(association.getOwnedClass().getAssociatedObjectByClass(association.getOwnerClass().getClassObject()));
		
		Set<String> extractedMethodNamesWithThisExpression = changeThisExpressionToIDInMethodInvocations(cu,extractedMethods,association);
		
		Set<MethodDeclaration> newExtractedMethods = extractOwnedServiceClass();
		
		ReplaceAssociatedFieldWithFKinOwnerClass();
		
		extractOwnerServiceClass();
		
		updateReferences(newExtractedMethods,extractedMethodNamesWithThisExpression);
		
		renameGetterSetterMethodsOfOwnerClass();
		
		IJavaProject project = cu.getJavaProject();
		return project;
	}
	
	
	public Set<String> changeThisExpressionToIDInMethodInvocations(ICompilationUnit cu,Set<MethodDeclaration>extractedMethods,final AssociationObject association) {
		//ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		//parser.setKind(ASTParser.K_COMPILATION_UNIT);
		//parser.setSource(cu);
        //parser.setResolveBindings(true);
        //Set<MethodDeclaration> extractedMethods = association.getOwnedClass().getMethodDeclarationsByField(association.getOwnedClass().getAssociatedObjectByClass(association.getOwnerClass().getClassObject()));
        //final CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
        //final ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
		final Set<String> methodNames = new HashSet<String>();
        MethodDeclaration[] methodDeclarations = extractedMethods.toArray(new MethodDeclaration[extractedMethods.size()]);
        for(int i=0;i<methodDeclarations.length;i++) {
        	final MethodDeclaration methodDeclaration = methodDeclarations[i];
	        System.out.println(methodDeclarations[i].getName());
	        final AST ast = methodDeclarations[i].getAST();
	        final ASTRewrite rewrite = ASTRewrite.create(ast);
	        //final List<SimpleName> simpleNames = new ArrayList<SimpleName>();
	        methodDeclaration.accept(new ASTVisitor() {
	            @Override
	            public boolean visit(MethodInvocation node) {
	                List<Expression> arguments = node.arguments();
	                for (Expression argument : arguments) {
	                    if (argument instanceof ThisExpression) {
	                    	 methodNames.add(methodDeclaration.getName().toString());
	                    	 FieldAccess newFieldAccess = ast.newFieldAccess();
	                         newFieldAccess.setExpression(ast.newThisExpression());
	                         newFieldAccess.setName(ast.newSimpleName(association.getOwnedClass().getIdField().getName()));
	                        //Name thisName = ast.newSimpleName("this.id");
	                        //Expression replacement = ast.newQualifiedName(id);
	                        rewrite.replace(argument, newFieldAccess, null);
	                    }
	                }
	                return super.visit(node);
	            }
	        });
	        TextEdit edits;
			try {
				edits = rewrite.rewriteAST();
				Document document = new Document(cu.getSource());
				edits.apply(document);
				cu.getBuffer().setContents(document.get());
				cu.save(null, true);
				// FIXME: the following is not an allowed operation in this context
				cu.commitWorkingCopy(true, null);
			} catch (JavaModelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        return methodNames;
	}
	
	public Set<MethodDeclaration> extractOwnedServiceClass() {
		IFile sourceFile = association.getOwnedClass().getClassObject().getIFile();
		ICompilationUnit cu = (ICompilationUnit)association.getOwnedClass().getClassObject().getITypeRoot().getPrimaryElement();
		Set<VariableDeclaration> extractedFieldFragments = new LinkedHashSet<VariableDeclaration>();
		extractedFieldFragments.add(association.getOwnedClass().getAssociatedObjectByClass(association.getOwnerClass().getClassObject()).getVariableDeclaration());
		final Set<MethodDeclaration> extractedMethods = association.getOwnedClass().getMethodDeclarationsByField(association.getOwnedClass().getAssociatedObjectByClass(association.getOwnerClass().getClassObject()));
		Set<MethodDeclaration> delegateMethods = new LinkedHashSet<MethodDeclaration>();
		

		
		ASTParser parser2 = ASTParser.newParser(ASTReader.JLS);
		parser2.setKind(ASTParser.K_COMPILATION_UNIT);
		parser2.setSource(cu);
        parser2.setResolveBindings(true);
        
        
        final Set<MethodDeclaration> newExtractedMethods = new HashSet<MethodDeclaration>();
		CompilationUnit sourceCompilationUnit = (CompilationUnit) parser2.createAST(null);
		
		
		sourceCompilationUnit.accept(new ASTVisitor() {
		    public boolean visit(MethodDeclaration node) {
		    	for(MethodDeclaration methodDeclaration:extractedMethods) {
		    		if(methodDeclaration.getName().toString().equals(node.getName().toString())) {
		    			newExtractedMethods.add(node);
		    		}
		    	}
		    	return true;
		    }
		});
		TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) sourceCompilationUnit.types().get(0);
		System.out.println(newExtractedMethods);
		Refactoring refactoring = new ExtractAttributeSlice(sourceFile, sourceCompilationUnit,
				sourceTypeDeclaration,
				extractedFieldFragments, newExtractedMethods,
				delegateMethods, OwnedServiceClassName,OwnedExtractedFieldIsSet,OwnedExtractedFieldName,OwnedExtractedFieldType,FKfieldName,FKtype);
		try {
			IJavaElement sourceJavaElement = JavaCore.create(sourceFile);
			JavaUI.openInEditor(sourceJavaElement);
		} catch (PartInitException e) {
			e.printStackTrace();
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		NullProgressMonitor monitor = new NullProgressMonitor();
	    try {
			refactoring.checkInitialConditions(monitor);
			refactoring.checkFinalConditions(monitor);
		    Change change = refactoring.createChange(monitor);
		    change.perform(monitor);
		} catch (OperationCanceledException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    MultiTextEdit sourceMultiTextEdit = new MultiTextEdit();
        CompilationUnitChange sourceCompilationUnitChange = new CompilationUnitChange("", cu);
        sourceCompilationUnitChange.setEdit(sourceMultiTextEdit);
        compilationUnitChanges.put(cu, sourceCompilationUnitChange);
        
	    FieldObject fieldObject = association.getOwnedClass().getAssociatedObjectByClass(association.getOwnerClass().getClassObject());
	    final FieldDeclaration fieldDeclaration = (FieldDeclaration)fieldObject.getVariableDeclaration().getParent();
	    parser2.setKind(ASTParser.K_COMPILATION_UNIT);
		parser2.setSource(cu);
        parser2.setResolveBindings(true);
	    sourceCompilationUnit = (CompilationUnit) parser2.createAST(null);
	    final ASTRewrite rewriter = ASTRewrite.create(sourceCompilationUnit.getAST());
	    sourceCompilationUnit.accept(new ASTVisitor() {
		    public boolean visit(FieldDeclaration node) {
		        // Find the FieldDeclaration node that you want to replace
		        if (node.toString().equals(fieldDeclaration.toString())) {
		        	rewriter.remove(node, null);
		        }
		        return true;
		    }
	    });
	    TextEdit edits;
		try {
			edits = rewriter.rewriteAST();
			ICompilationUnit sourceICompilationUnit = (ICompilationUnit)sourceCompilationUnit.getJavaElement();
			CompilationUnitChange change = compilationUnitChanges.get(sourceICompilationUnit);
			change.getEdit().addChild(edits);
			change.addTextEditGroup(new TextEditGroup("Change access of extracted member", new TextEdit[] {edits}));
			//IProgressMonitor monitor = new NullProgressMonitor();
			change.perform(monitor);
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    return newExtractedMethods;
	    
	}
	
	
	public void ReplaceAssociatedFieldWithFKinOwnerClass() {
		ICompilationUnit cuOwner = (ICompilationUnit)association.getOwnerClass().getClassObject().getITypeRoot().getPrimaryElement();
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cuOwner);
        parser.setResolveBindings(true);
        
        MultiTextEdit sourceMultiTextEdit = new MultiTextEdit();
        CompilationUnitChange sourceCompilationUnitChange = new CompilationUnitChange("", cuOwner);
        sourceCompilationUnitChange.setEdit(sourceMultiTextEdit);
        compilationUnitChanges.put(cuOwner, sourceCompilationUnitChange);
        
        
        
        FieldObject fieldObject = association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject());
        final FieldDeclaration fieldDeclaration = (FieldDeclaration)fieldObject.getVariableDeclaration().getParent();
		final CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		astRoot.recordModifications();
		final ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
		astRoot.accept(new ASTVisitor() {
		    public boolean visit(FieldDeclaration node) {
		        // Find the FieldDeclaration node that you want to replace
		        if (node.toString().equals(fieldDeclaration.toString())) {
		            // Create a new FieldDeclaration node with the desired changes
		            FieldDeclaration newFieldDeclaration = astRoot.getAST().newFieldDeclaration(astRoot.getAST().newVariableDeclarationFragment());
		            Type type = astRoot.getAST().newSimpleType(astRoot.getAST().newName(FKtype));
		            newFieldDeclaration.setType(type);
		            ListRewrite listRewrite = rewriter.getListRewrite(newFieldDeclaration, FieldDeclaration.MODIFIERS2_PROPERTY);
		            listRewrite.insertLast(astRoot.getAST().newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD), null);
		            //newFieldDeclaration.modifiers().addAll(node.modifiers());
		            SimpleName newName = astRoot.getAST().newSimpleName(FKfieldName);
		            
		            ((VariableDeclarationFragment) newFieldDeclaration.fragments().get(0)).setName(newName);
		            
		            NormalAnnotation annotation = astRoot.getAST().newNormalAnnotation();
		            //annotation.setTypeName(astRoot.getAST().newSimpleName("@Column(name=\""+arr[1]+"\")"));
		            annotation.setTypeName(astRoot.getAST().newSimpleName("Column"));
		            MemberValuePair pair = astRoot.getAST().newMemberValuePair();
		            pair.setName(astRoot.getAST().newSimpleName("name"));
		            StringLiteral literal = astRoot.getAST().newStringLiteral();
		            literal.setLiteralValue(FKfieldName);
		            pair.setValue(literal);
		            annotation.values().add(pair);
		            rewriter.getListRewrite(newFieldDeclaration, FieldDeclaration.MODIFIERS2_PROPERTY).insertFirst(annotation, null);

		            // Replace the old FieldDeclaration node with the new one
		            //TypeDeclaration typeDeclaration = (TypeDeclaration) astRoot.types().get(0);
		            //ListRewrite listRewriteTypeDecl = rewriter.getListRewrite(typeDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		            rewriter.replace(node, newFieldDeclaration, null);
		        }
		        return true;
		    }
		});
		
		String previousTypeFull[] = fieldObject.getType().toString().split("\\.");
		final String previousType=previousTypeFull[previousTypeFull.length-1];
		Set<MethodDeclaration> extractedMethods2 = association.getOwnerClass().getMethodDeclarationsByField(association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject()));
        MethodDeclaration[] methodDeclarations = extractedMethods2.toArray(new MethodDeclaration[extractedMethods2.size()]);
        for(int i=0;i<methodDeclarations.length;i++) {
        	MethodDeclaration methodDeclaration = methodDeclarations[i];
        	ReplaceFieldWithFieldIdInsideMethod(cuOwner,methodDeclaration,FKtype,FKfieldName,fieldObject.getName(),previousType);
        	ChangeReturnTypeForMethod(cuOwner,methodDeclaration,FKtype);
        }
        
        
		TextEdit edits;
		try {
			edits = rewriter.rewriteAST();
			ICompilationUnit sourceICompilationUnit = (ICompilationUnit)astRoot.getJavaElement();
			CompilationUnitChange change = compilationUnitChanges.get(sourceICompilationUnit);
			change.getEdit().addChild(edits);
			change.addTextEditGroup(new TextEditGroup("Change access of extracted member", new TextEdit[] {edits}));
			IProgressMonitor monitor = new NullProgressMonitor();
			change.perform(monitor);
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void ReplaceFieldWithFieldIdInsideMethod(ICompilationUnit cu,final MethodDeclaration methodDeclaration,String type,final String fieldName,final String previousFieldName,String previousType) {
		//final MethodDeclaration methodDeclaration = methodDeclarations[i];
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
        parser.setResolveBindings(true);
        final CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
    	final AST ast = methodDeclaration.getAST();
        final ASTRewrite rewriter = ASTRewrite.create(ast);
        for (Object obj : methodDeclaration.parameters()) {
            SingleVariableDeclaration param = (SingleVariableDeclaration) obj;
            if (param.getType().toString().equals(previousType)) {
                SingleVariableDeclaration newParam = methodDeclaration.getAST().newSingleVariableDeclaration();
                //
                newParam.setType(methodDeclaration.getAST().newSimpleType(methodDeclaration.getAST().newName(type)));
                newParam.setName(methodDeclaration.getAST().newSimpleName(fieldName));
                //methodDeclaration.parameters().set(methodDeclaration.parameters().indexOf(param), newParam);
                rewriter.replace(param, newParam, null);
            }
        }
        methodDeclaration.accept(new ASTVisitor() {
            public boolean visit(SimpleName name) {
                if (name.getIdentifier().equals(previousFieldName)) {
                    SimpleName newName = methodDeclaration.getAST().newSimpleName(fieldName);
                    rewriter.replace(name, newName, null);
                }
                return true;
            }
        });
        TextEdit edits;
		try {
			edits = rewriter.rewriteAST();
			ICompilationUnit sourceICompilationUnit = (ICompilationUnit)astRoot.getJavaElement();
			CompilationUnitChange change = compilationUnitChanges.get(sourceICompilationUnit);
			change.getEdit().addChild(edits);
			change.addTextEditGroup(new TextEditGroup("Change access of extracted member", new TextEdit[] {edits}));
			//cu.save(null, true);
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void ChangeReturnTypeForMethod(ICompilationUnit cu,MethodDeclaration methodDeclaration,String returnTypeName) {
		//final MethodDeclaration methodDeclaration = methodDeclarations[i];
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
        parser.setResolveBindings(true);
        final CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
    	final AST ast = methodDeclaration.getAST();
        final ASTRewrite rewriter = ASTRewrite.create(ast);
        Type returnType = methodDeclaration.getReturnType2();
        if(returnType.toString().equals(OwnerExtractedFieldType)) {
        	Type type = ast.newSimpleType(ast.newName(returnTypeName));
        	rewriter.replace(returnType, type, null);
        }
        TextEdit edits;
		try {
			edits = rewriter.rewriteAST();
			ICompilationUnit sourceICompilationUnit = (ICompilationUnit)astRoot.getJavaElement();
			CompilationUnitChange change = compilationUnitChanges.get(sourceICompilationUnit);
			change.getEdit().addChild(edits);
			change.addTextEditGroup(new TextEditGroup("Change access of extracted member", new TextEdit[] {edits}));
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void extractOwnerServiceClass() {
		IFile sourceFile = association.getOwnerClass().getClassObject().getIFile();
		ICompilationUnit cu = (ICompilationUnit)association.getOwnerClass().getClassObject().getITypeRoot().getPrimaryElement();

		
		Set<VariableDeclaration> extractedFieldFragments = new LinkedHashSet<VariableDeclaration>();
		extractedFieldFragments.add(association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject()).getVariableDeclaration());
		final Set<MethodDeclaration> extractedMethods = association.getOwnerClass().getMethodDeclarationsByField(association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject()));
		Set<MethodDeclaration> delegateMethods = new LinkedHashSet<MethodDeclaration>();
		String[] array2 = association.getOwnedClass().getClassObject().getName().split("\\.");
        final String className = array2[array2.length-1];
		
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
        parser.setResolveBindings(true);
        
        //final Set<MethodDeclaration> extractedMethods = new HashSet<MethodDeclaration>();
        
        final Set<MethodDeclaration> newExtractedMethods = new HashSet<MethodDeclaration>();
		CompilationUnit sourceCompilationUnit = (CompilationUnit) parser.createAST(null);
		sourceCompilationUnit.accept(new ASTVisitor() {
		    public boolean visit(MethodDeclaration node) {
		    	for(MethodDeclaration methodDeclaration:extractedMethods) {
		    		if((methodDeclaration.getName().toString().equals(node.getName().toString()))&&(!methodDeclaration.getName().toString().equals("get"+className)
		    				&&(!methodDeclaration.getName().toString().equals("set"+className)))) {
		    			newExtractedMethods.add(node);
		    		}
		    	}
		    	return true;
		    }
		});
		TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) sourceCompilationUnit.types().get(0);
		
		Refactoring refactoring = new ExtractAttributeSlice(sourceFile, sourceCompilationUnit,
				sourceTypeDeclaration,
				extractedFieldFragments, newExtractedMethods,
				delegateMethods, OwnerServiceClassName,OwnerExtractedFieldIsSet,OwnerExtractedFieldName,OwnerExtractedFieldType,FKfieldName,FKtype);
		try {
			IJavaElement sourceJavaElement = JavaCore.create(sourceFile);
			JavaUI.openInEditor(sourceJavaElement);
		} catch (PartInitException e) {
			e.printStackTrace();
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		NullProgressMonitor monitor = new NullProgressMonitor();
	    try {
			refactoring.checkInitialConditions(monitor);
			refactoring.checkFinalConditions(monitor);
		    Change change = refactoring.createChange(monitor);
		    change.perform(monitor);
		} catch (OperationCanceledException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void updateReferences(final Set<MethodDeclaration> newExtractedMethods,final Set<String> extractedMethodNamesWithThisExpression) {
		for(final ClassObject classObject :classes) {
			ICompilationUnit icu = (ICompilationUnit)classObject.getITypeRoot().getPrimaryElement();
			//ICompilationUnit icu = (ICompilationUnit)association.getOwnerClass().getClassObject().getITypeRoot().getPrimaryElement();
			
			MultiTextEdit sourceMultiTextEdit2 = new MultiTextEdit();
	        CompilationUnitChange sourceCompilationUnitChange2 = new CompilationUnitChange("", icu);
	        sourceCompilationUnitChange2.setEdit(sourceMultiTextEdit2);
	        compilationUnitChanges.put(icu, sourceCompilationUnitChange2);
			
			ASTParser parser3 = ASTParser.newParser(ASTReader.JLS);
			parser3.setKind(ASTParser.K_COMPILATION_UNIT);
			parser3.setSource(icu);
	        parser3.setResolveBindings(true);
	        final CompilationUnit astRoot3 = (CompilationUnit) parser3.createAST(null);
			astRoot3.recordModifications();
			final ASTRewrite rewriter3 = ASTRewrite.create(astRoot3.getAST());
			final String OwnedExtractedClassFieldName = Character.toLowerCase(OwnedServiceClassName.charAt(0)) + OwnedServiceClassName.substring(1);
			final String OwnerExtractedClassFieldName = Character.toLowerCase(OwnerServiceClassName.charAt(0)) + OwnerServiceClassName.substring(1);
			final String OwnerAssociatedFieldName = Character.toUpperCase(OwnerExtractedFieldName.charAt(0)) + OwnerExtractedFieldName.substring(1);
			
			
			List<MethodDeclaration> classMethodDeclarations = new ArrayList<MethodDeclaration>();
			for(MethodObject methObj:classObject.getMethodList()) {
				classMethodDeclarations.add(methObj.getMethodDeclaration());
			}
			
			astRoot3.accept(new ASTVisitor() {
				
				public boolean visit(final MethodDeclaration md) {
					md.accept(new ASTVisitor() {
						//boolean hasMoreThanOneInvocationOfExtractedMethod = false;
						//boolean hasMoreThanOneInvocationOfExtractedMethod2 = false;
						public boolean visit(MethodInvocation mi) {
							for(MethodDeclaration methodDeclaration:newExtractedMethods) {
								if(mi.getName().toString().equals(methodDeclaration.getName().toString())) {
									//if(!hasMoreThanOneInvocationOfExtractedMethod) {
										//hasMoreThanOneInvocationOfExtractedMethod = true;
										createImport(astRoot3,rewriter3,OwnedServiceClassName);
										
										VariableDeclarationFragment fragment = astRoot3.getAST().newVariableDeclarationFragment();
										fragment.setName(astRoot3.getAST().newSimpleName(OwnedExtractedClassFieldName));
										MethodInvocation invocation = astRoot3.getAST().newMethodInvocation();
										invocation.setName(astRoot3.getAST().newSimpleName("factoryMethod"));
										invocation.setExpression(astRoot3.getAST().newSimpleName(OwnedServiceClassName));
										//TypeDeclaration type = (TypeDeclaration) methodDeclaration.getParent();
										
										addExpressionAsArgumentToInvocation(astRoot3,mi,invocation,association,rewriter3);
										
										Type serviceType = astRoot3.getAST().newSimpleType(astRoot3.getAST().newName(OwnedServiceClassName));
										fragment.setInitializer(invocation);
										VariableDeclarationStatement statement = astRoot3.getAST().newVariableDeclarationStatement(fragment);
										statement.setType(serviceType);
										System.out.println(findFirstStatement(mi).getParent());
										Block block = (Block) findFirstStatement(mi).getParent();
										ListRewrite listRewrite = rewriter3.getListRewrite(block, Block.STATEMENTS_PROPERTY);
										listRewrite.insertBefore(statement, findFirstStatement(mi), null);
									//}
									for(String name:extractedMethodNamesWithThisExpression) {
										if(name.equals(mi.getName().toString())) {
											addExpressionAsArgumentToInvocation(astRoot3,mi,mi,association,rewriter3);
										}
									}
									rewriter3.replace(mi.getExpression(), astRoot3.getAST().newSimpleName(OwnedExtractedClassFieldName), null);
								}
							}
							if(mi.getName().toString().equals("get"+OwnerAssociatedFieldName)) {
								//if(!hasMoreThanOneInvocationOfExtractedMethod2) {
									//hasMoreThanOneInvocationOfExtractedMethod2 = true;
									createImport(astRoot3,rewriter3,OwnerServiceClassName);
									
									VariableDeclarationFragment fragment = astRoot3.getAST().newVariableDeclarationFragment();
									fragment.setName(astRoot3.getAST().newSimpleName(OwnerExtractedClassFieldName));
									MethodInvocation invocation = astRoot3.getAST().newMethodInvocation();
									invocation.setName(astRoot3.getAST().newSimpleName("factoryMethod"));
									invocation.setExpression(astRoot3.getAST().newSimpleName(OwnerServiceClassName));
									ListRewrite listRewrite2 = rewriter3.getListRewrite(invocation, MethodInvocation.ARGUMENTS_PROPERTY);
									listRewrite2.insertLast(mi, null);
									
									Type serviceType = astRoot3.getAST().newSimpleType(astRoot3.getAST().newName(OwnerServiceClassName));
									fragment.setInitializer(invocation);
									VariableDeclarationStatement statement = astRoot3.getAST().newVariableDeclarationStatement(fragment);
									statement.setType(serviceType);
									Block block = (Block) findFirstStatement(mi).getParent();
									ListRewrite listRewrite = rewriter3.getListRewrite(block, Block.STATEMENTS_PROPERTY);
									listRewrite.insertBefore(statement, findFirstStatement(mi), null);
								//}
								MethodInvocation newExpression = astRoot3.getAST().newMethodInvocation();
								newExpression.setExpression(astRoot3.getAST().newSimpleName(OwnerExtractedClassFieldName));
								newExpression.setName(astRoot3.getAST().newSimpleName("query"+OwnerAssociatedFieldName));
								rewriter3.replace(mi, newExpression, null);
							}
							return true;
						}
					});;
					return true;
				}
			});
			TextEdit edits2;
			try {
				edits2 = rewriter3.rewriteAST();
				icu = (ICompilationUnit)astRoot3.getJavaElement();
				CompilationUnitChange change = compilationUnitChanges.get(icu);
				change.getEdit().addChild(edits2);
				change.addTextEditGroup(new TextEditGroup("Change access of extracted member", new TextEdit[] {edits2}));
				IProgressMonitor monitor = new NullProgressMonitor();
				change.perform(monitor);
			} catch (JavaModelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
	public void addExpressionAsArgumentToInvocation(CompilationUnit astRoot,MethodInvocation originalMI,MethodInvocation newMI,AssociationObject association,ASTRewrite rewriter) {
		Expression exp = originalMI.getExpression();
		String typeName = null;
		ITypeBinding typeBinding = originalMI.getExpression().resolveTypeBinding();
		if (typeBinding != null) {
		    typeName = typeBinding.getQualifiedName();
		    //System.out.println(typeName);
		}
		if(typeName.equals(association.getOwnedClass().getClassObject().getName())) {
			String getterMethodName = association.getOwnedClass().getIdFieldGetterName();
			MethodInvocation newExpression = astRoot.getAST().newMethodInvocation();
			newExpression.setExpression(astRoot.getAST().newSimpleName(exp.toString()));
			newExpression.setName(astRoot.getAST().newSimpleName(getterMethodName));

			ListRewrite listRewrite2 = rewriter.getListRewrite(newMI, MethodInvocation.ARGUMENTS_PROPERTY);
			listRewrite2.insertLast(newExpression, null);
		}else {
			ListRewrite listRewrite2 = rewriter.getListRewrite(newMI, MethodInvocation.ARGUMENTS_PROPERTY);
			listRewrite2.insertLast(originalMI.getExpression(), null);
		}
	}
	
	public ASTNode findFirstStatement(ASTNode node) {
		if(node instanceof Statement) {
			return node;
		}else {
			return findFirstStatement(node.getParent());
		}
	}
	
	public void createImport(CompilationUnit astRoot,ASTRewrite rewriter,String serviceClassName) {
		boolean needsServiceClassImport = true;
		String[] tempPackageName = association.getOwnerClass().getClassObject().getName().split("\\.");
		tempPackageName[tempPackageName.length-1] = serviceClassName;
		String[] serviceClassImportName = tempPackageName;
		StringBuilder newstring = new StringBuilder();
		for(int i=0;i<tempPackageName.length-1;i++) {
			newstring.append(tempPackageName[i]);
			newstring.append(".");
		}
		String serviceClassFullName = newstring.toString()+serviceClassName;
		String packageName = newstring.toString()+"*";
		for(Object o:astRoot.imports()) {
			ImportDeclaration importDeclaration = (ImportDeclaration) o;
			//System.out.println(importDeclaration.getName());
			if(importDeclaration.getName().toString().equals(serviceClassFullName)||importDeclaration.getName().toString().equals(packageName)) {
				needsServiceClassImport = false;
				break;
			}
		}
		if(needsServiceClassImport) {
			ImportDeclaration id = astRoot.getAST().newImportDeclaration();
	        id.setName(astRoot.getAST().newName(serviceClassImportName));
	        ListRewrite ImportlistRewrite = rewriter.getListRewrite(astRoot, CompilationUnit.IMPORTS_PROPERTY);
	        ImportlistRewrite.insertLast(id, null);
		}
	}
	
	public void renameGetterSetterMethodsOfOwnerClass() {
		ICompilationUnit unit = (ICompilationUnit)association.getOwnerClass().getClassObject().getITypeRoot().getPrimaryElement();
		String[] array2 = association.getOwnedClass().getClassObject().getName().split("\\.");
        final String className = array2[array2.length-1];
        final String FKfieldNameMethodName = Character.toUpperCase(FKfieldName.charAt(0)) + FKfieldName.substring(1);
		
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(unit);
        parser.setResolveBindings(true);
        
        //final Set<MethodDeclaration> extractedMethods = new HashSet<MethodDeclaration>();
        
		CompilationUnit sourceCompilationUnit = (CompilationUnit) parser.createAST(null);
		sourceCompilationUnit.accept(new ASTVisitor() {
		    public boolean visit(MethodDeclaration node) {
		    	if(node.getName().toString().equals("get"+className)){
		    		renameMethodsOfOwnerClass(node,"get"+FKfieldNameMethodName);
		    	}else if(node.getName().toString().equals("set"+className)) {
		    		renameMethodsOfOwnerClass(node,"set"+FKfieldNameMethodName);
		    	}
		    	return true;
		    }
		});
	}
	
	public void renameMethodsOfOwnerClass(MethodDeclaration methodDeclaration,String newName){
		ICompilationUnit unit = (ICompilationUnit)association.getOwnerClass().getClassObject().getITypeRoot().getPrimaryElement();
		IBinding binding = methodDeclaration.resolveBinding();
		IMethod method = (IMethod) binding.getJavaElement();
		
		RefactoringContribution contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.RENAME_METHOD);
        RenameJavaElementDescriptor descriptor = (RenameJavaElementDescriptor) contribution.createDescriptor();
        descriptor.setProject(unit.getResource().getProject().getName( ));
        descriptor.setNewName(newName); // new name for a Class
        descriptor.setJavaElement(method);
        descriptor.setUpdateReferences(true);
        RefactoringStatus status = new RefactoringStatus();
        try {
            Refactoring refactoring = descriptor.createRefactoring(status);

            IProgressMonitor monitor = new NullProgressMonitor();
            refactoring.checkInitialConditions(monitor);
            refactoring.checkFinalConditions(monitor);
            Change change = refactoring.createChange(monitor);
            change.perform(monitor);

        } catch (CoreException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
	}
	
}
