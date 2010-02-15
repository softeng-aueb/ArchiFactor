package gr.uom.java.ast.decomposition.cfg;

import gr.uom.java.ast.MethodObject;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclaration;

public class PDGSlice extends Graph {
	private MethodObject method;
	private BasicBlock boundaryBlock;
	private PDGNode nodeCriterion;
	private AbstractVariable localVariableCriterion;
	private Set<PDGNode> sliceNodes;
	private Set<PDGNode> remainingNodes;
	private Set<AbstractVariable> passedParameters;
	private Set<PDGNode> indispensableNodes;
	private Set<PDGNode> removableNodes;
	private Set<AbstractVariable> returnedVariablesInOriginalMethod;
	private IFile iFile;
	
	public PDGSlice(PDG pdg, BasicBlock boundaryBlock, PDGNode nodeCriterion,
			AbstractVariable localVariableCriterion) {
		super();
		this.method = pdg.getMethod();
		this.iFile = pdg.getIFile();
		this.returnedVariablesInOriginalMethod = pdg.getReturnedVariables();
		this.boundaryBlock = boundaryBlock;
		this.nodeCriterion = nodeCriterion;
		this.localVariableCriterion = localVariableCriterion;
		Set<PDGNode> regionNodes = pdg.blockBasedRegion(boundaryBlock);
		for(PDGNode node : regionNodes) {
			nodes.add(node);
		}
		for(GraphEdge edge : pdg.edges) {
			PDGDependence dependence = (PDGDependence)edge;
			if(nodes.contains(dependence.src) && nodes.contains(dependence.dst)) {
				if(dependence instanceof PDGDataDependence) {
					PDGDataDependence dataDependence = (PDGDataDependence)dependence;
					if(dataDependence.isLoopCarried()) {
						PDGNode loopNode = dataDependence.getLoop().getPDGNode();
						if(nodes.contains(loopNode))
							edges.add(dataDependence);
					}
					else
						edges.add(dataDependence);
				}
				else if(dependence instanceof PDGAntiDependence) {
					PDGAntiDependence antiDependence = (PDGAntiDependence)dependence;
					if(antiDependence.isLoopCarried()) {
						PDGNode loopNode = antiDependence.getLoop().getPDGNode();
						if(nodes.contains(loopNode))
							edges.add(antiDependence);
					}
					else
						edges.add(antiDependence);
				}
				else
					edges.add(dependence);
			}
		}
		this.sliceNodes = new TreeSet<PDGNode>();
		sliceNodes.addAll(computeSlice(nodeCriterion, localVariableCriterion));
		this.remainingNodes = new TreeSet<PDGNode>();
		remainingNodes.add(pdg.getEntryNode());
		for(GraphNode node : pdg.nodes) {
			PDGNode pdgNode = (PDGNode)node;
			if(!sliceNodes.contains(pdgNode))
				remainingNodes.add(pdgNode);
		}
		this.passedParameters = new LinkedHashSet<AbstractVariable>();
		Set<PDGNode> nCD = new LinkedHashSet<PDGNode>();
		Set<PDGNode> nDD = new LinkedHashSet<PDGNode>();
		for(GraphEdge edge : pdg.edges) {
			PDGDependence dependence = (PDGDependence)edge;
			PDGNode srcPDGNode = (PDGNode)dependence.src;
			PDGNode dstPDGNode = (PDGNode)dependence.dst;
			if(dependence instanceof PDGDataDependence) {
				PDGDataDependence dataDependence = (PDGDataDependence)dependence;
				if(remainingNodes.contains(srcPDGNode) && sliceNodes.contains(dstPDGNode))
					passedParameters.add(dataDependence.getData());
				if(sliceNodes.contains(srcPDGNode) && remainingNodes.contains(dstPDGNode) &&
						!dataDependence.getData().equals(localVariableCriterion))
					nDD.add(srcPDGNode);
			}
			else if(dependence instanceof PDGControlDependence) {
				if(sliceNodes.contains(srcPDGNode) && remainingNodes.contains(dstPDGNode))
					nCD.add(srcPDGNode);
			}
		}
		Set<PDGNode> controlIndispensableNodes = new LinkedHashSet<PDGNode>();
		for(PDGNode p : nCD) {
			for(AbstractVariable usedVariable : p.usedVariables) {
				Set<PDGNode> pSliceNodes = computeSlice(p, usedVariable);
				for(GraphNode node : pdg.nodes) {
					PDGNode q = (PDGNode)node;
					if(pSliceNodes.contains(q) || q.equals(p))
						controlIndispensableNodes.add(q);
				}
			}
		}
		Set<PDGNode> dataIndispensableNodes = new LinkedHashSet<PDGNode>();
		for(PDGNode p : nDD) {
			for(AbstractVariable definedVariable : p.definedVariables) {
				Set<PDGNode> pSliceNodes = computeSlice(p, definedVariable);
				for(GraphNode node : pdg.nodes) {
					PDGNode q = (PDGNode)node;
					if(pSliceNodes.contains(q))
						dataIndispensableNodes.add(q);
				}
			}
		}
		this.indispensableNodes = new TreeSet<PDGNode>();
		indispensableNodes.addAll(controlIndispensableNodes);
		indispensableNodes.addAll(dataIndispensableNodes);
		this.removableNodes = new LinkedHashSet<PDGNode>();
		for(GraphNode node : pdg.nodes) {
			PDGNode pdgNode = (PDGNode)node;
			if(!remainingNodes.contains(pdgNode) && !indispensableNodes.contains(pdgNode))
				removableNodes.add(pdgNode);
		}
	}

	public MethodObject getMethod() {
		return method;
	}

	public IFile getIFile() {
		return iFile;
	}

	public BasicBlock getBoundaryBlock() {
		return boundaryBlock;
	}

	public PDGNode getExtractedMethodInvocationInsertionNode() {
		return ((TreeSet<PDGNode>)sliceNodes).first();
	}

	public PDGNode getNodeCriterion() {
		return nodeCriterion;
	}

	public AbstractVariable getLocalVariableCriterion() {
		return localVariableCriterion;
	}

	public Set<PDGNode> getSliceNodes() {
		return sliceNodes;
	}

	public Set<AbstractVariable> getPassedParameters() {
		return passedParameters;
	}

	public Set<PDGNode> getRemovableNodes() {
		return removableNodes;
	}

	public boolean edgeBelongsToBlockBasedRegion(GraphEdge edge) {
		return edges.contains(edge);
	}

	public boolean declarationOfVariableCriterionBelongsToSliceNodes() {
		PlainVariable plainVariable = null;
		if(localVariableCriterion instanceof PlainVariable) {
			plainVariable = (PlainVariable)localVariableCriterion;
		}
		else if(localVariableCriterion instanceof CompositeVariable) {
			CompositeVariable compositeVariable = (CompositeVariable)localVariableCriterion;
			plainVariable = new PlainVariable(compositeVariable.getName());
		}
		for(PDGNode pdgNode : sliceNodes) {
			if(pdgNode.declaresLocalVariable(plainVariable))
				return true;
		}
		return false;
	}

	public boolean declarationOfVariableCriterionBelongsToRemovableNodes() {
		PlainVariable plainVariable = null;
		if(localVariableCriterion instanceof PlainVariable) {
			plainVariable = (PlainVariable)localVariableCriterion;
		}
		else if(localVariableCriterion instanceof CompositeVariable) {
			CompositeVariable compositeVariable = (CompositeVariable)localVariableCriterion;
			plainVariable = new PlainVariable(compositeVariable.getName());
		}
		for(PDGNode pdgNode : removableNodes) {
			if(pdgNode.declaresLocalVariable(plainVariable))
				return true;
		}
		return false;
	}

	public boolean nodeCriterionIsDuplicated() {
		Set<PDGNode> duplicatedNodes = new LinkedHashSet<PDGNode>();
		duplicatedNodes.addAll(sliceNodes);
		duplicatedNodes.retainAll(indispensableNodes);
		if(duplicatedNodes.contains(nodeCriterion))
			return true;
		return false;
	}

	public boolean satisfiesRules() {
		if(!nodeCritetionIsDeclarationOfVariableCriterion() &&
				!variableCriterionIsReturnedVariableInOriginalMethod() &&
				!containsBreakContinueReturnSliceNode() &&
				!containsDuplicateNodeWithStateChangingMethodInvocation() &&
				!nonDuplicatedSliceNodeAntiDependsOnNonRemovableNode() &&
				!duplicatedSliceNodeWithClassInstantiationHasDependenceOnRemovableNode())
			return true;
		return false;
	}

	private boolean nonDuplicatedSliceNodeAntiDependsOnNonRemovableNode() {
		Set<PDGNode> duplicatedNodes = new LinkedHashSet<PDGNode>();
		duplicatedNodes.addAll(sliceNodes);
		duplicatedNodes.retainAll(indispensableNodes);
		for(PDGNode sliceNode : sliceNodes) {
			if(!duplicatedNodes.contains(sliceNode)) {
				for(GraphEdge edge : sliceNode.incomingEdges) {
					PDGDependence dependence = (PDGDependence)edge;
					if(edges.contains(dependence) && dependence instanceof PDGAntiDependence) {
						PDGAntiDependence antiDependence = (PDGAntiDependence)dependence;
						PDGNode srcPDGNode = (PDGNode)antiDependence.src;
						if(!removableNodes.contains(srcPDGNode))
							return true;
					}
				}
			}
		}
		return false;
	}

	private boolean duplicatedSliceNodeWithClassInstantiationHasDependenceOnRemovableNode() {
		Set<PDGNode> duplicatedNodes = new LinkedHashSet<PDGNode>();
		duplicatedNodes.addAll(sliceNodes);
		duplicatedNodes.retainAll(indispensableNodes);
		for(PDGNode duplicatedNode : duplicatedNodes) {
			if(duplicatedNode.containsClassInstanceCreation()) {
				Map<VariableDeclaration, ClassInstanceCreation> classInstantiations = duplicatedNode.getClassInstantiations();
				for(VariableDeclaration variableDeclaration : classInstantiations.keySet()) {
					for(GraphEdge edge : duplicatedNode.outgoingEdges) {
						PDGDependence dependence = (PDGDependence)edge;
						if(edges.contains(dependence) && dependence instanceof PDGDependence) {
							PDGDependence dataDependence = (PDGDependence)dependence;
							PDGNode dstPDGNode = (PDGNode)dataDependence.dst;
							if(removableNodes.contains(dstPDGNode)) {
								if(dstPDGNode.changesStateOfReference(variableDeclaration) ||
										dstPDGNode.assignsReference(variableDeclaration) || dstPDGNode.accessesReference(variableDeclaration))
									return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	private boolean containsBreakContinueReturnSliceNode() {
		for(PDGNode node : sliceNodes) {
			Statement statement = node.getASTStatement();
			if(statement instanceof BreakStatement || statement instanceof ContinueStatement ||
					statement instanceof ReturnStatement)
				return true;
		}
		return false;
	}

	private boolean nodeCritetionIsDeclarationOfVariableCriterion() {
		if(nodeCriterion.declaresLocalVariable(localVariableCriterion))
			return true;
		return false;
	}

	private boolean variableCriterionIsReturnedVariableInOriginalMethod() {
		if(returnedVariablesInOriginalMethod.contains(localVariableCriterion))
			return true;
		return false;
	}

	private boolean containsDuplicateNodeWithStateChangingMethodInvocation() {
		Set<PDGNode> duplicatedNodes = new LinkedHashSet<PDGNode>();
		duplicatedNodes.addAll(sliceNodes);
		duplicatedNodes.retainAll(indispensableNodes);
		for(PDGNode node : duplicatedNodes) {
			for(AbstractVariable stateChangingVariable : node.getStateChangingVariables()) {
				PlainVariable plainVariable = null;
				if(stateChangingVariable instanceof PlainVariable) {
					plainVariable = (PlainVariable)stateChangingVariable;
				}
				else if(stateChangingVariable instanceof CompositeVariable) {
					CompositeVariable compositeVariable = (CompositeVariable)stateChangingVariable;
					plainVariable = new PlainVariable(compositeVariable.getName());
				}
				if(!sliceContainsDeclaration(plainVariable))
					return true;
			}
		}
		return false;
	}

	private boolean sliceContainsDeclaration(AbstractVariable variableDeclaration) {
		for(PDGNode pdgNode : sliceNodes) {
			if(pdgNode.declaresLocalVariable(variableDeclaration))
				return true;
		}
		return false;
	}

	private Set<PDGNode> computeSlice(PDGNode nodeCriterion, AbstractVariable localVariableCriterion) {
		Set<PDGNode> sliceNodes = new LinkedHashSet<PDGNode>();
		if(nodeCriterion.definesLocalVariable(localVariableCriterion)) {
			sliceNodes.addAll(traverseBackward(nodeCriterion, new LinkedHashSet<PDGNode>()));
		}
		else if(nodeCriterion.usesLocalVariable(localVariableCriterion)) {
			Set<PDGNode> defNodes = getDefNodes(nodeCriterion, localVariableCriterion);
			for(PDGNode defNode : defNodes) {
				sliceNodes.addAll(traverseBackward(defNode, new LinkedHashSet<PDGNode>()));
			}
		}
		return sliceNodes;
	}

	private Set<PDGNode> getDefNodes(PDGNode node, AbstractVariable localVariable) {
		Set<PDGNode> defNodes = new LinkedHashSet<PDGNode>();
		for(GraphEdge edge : node.incomingEdges) {
			PDGDependence dependence = (PDGDependence)edge;
			if(edges.contains(dependence) && dependence instanceof PDGDataDependence) {
				PDGDataDependence dataDependence = (PDGDataDependence)dependence;
				if(dataDependence.getData().equals(localVariable)) {
					PDGNode srcPDGNode = (PDGNode)dependence.src;
					defNodes.add(srcPDGNode);
				}
			}
		}
		return defNodes;
	}

	private Set<PDGNode> traverseBackward(PDGNode node, Set<PDGNode> visitedNodes) {
		Set<PDGNode> sliceNodes = new LinkedHashSet<PDGNode>();
		sliceNodes.add(node);
		visitedNodes.add(node);
		for(GraphEdge edge : node.incomingEdges) {
			PDGDependence dependence = (PDGDependence)edge;
			if(edges.contains(dependence) && !(dependence instanceof PDGAntiDependence)) {
				PDGNode srcPDGNode = (PDGNode)dependence.src;
				if(!visitedNodes.contains(srcPDGNode))
					sliceNodes.addAll(traverseBackward(srcPDGNode, visitedNodes));
			}
		}
		return sliceNodes;
	}

	public String toString() {
		return "<" + localVariableCriterion + ", " + nodeCriterion.getId() + "> [B" + boundaryBlock.getId() + "]\n" +
		sliceNodes + "\npassed parameters: " + passedParameters + "\nindispensable nodes: " + indispensableNodes;
	}
}
