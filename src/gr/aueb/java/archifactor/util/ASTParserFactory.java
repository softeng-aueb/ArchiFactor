package gr.aueb.java.archifactor.util;

import org.eclipse.jdt.core.dom.ASTParser;

import gr.uom.java.ast.ASTReader;

public class ASTParserFactory {

	
	public static ASTParser getParser() {
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        return parser;
	}
	
}
