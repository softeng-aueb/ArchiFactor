package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import gr.aueb.java.archifactor.jpa.enums.JpaRelationshipType;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;

public class ServiceMethodProviderFactory {

	public static ServiceMethodProvider createProvider(RelationshipInfo relationship) {
		JpaRelationshipType relationshipType = relationship.getRelationshipType();
		if (relationshipType == JpaRelationshipType.MANY_TO_ONE) {
			return ManyToOneServiceMethodProvider.fromRelationship(relationship);
		} else if (relationshipType == JpaRelationshipType.ONE_TO_MANY) {
			return OneToManyServiceMethodProvider.fromRelationship(relationship);
		} else if (relationshipType == JpaRelationshipType.MANY_TO_MANY) {
			if (relationship.isOwningSide()) {
				return ManyToManyOwningServiceMethodProvider.fromRelationship(relationship);
			} else {
				return ManyToManyNonOwningServiceMethodProvider.fromRelationship(relationship);
			}
		}
		return null;
	}
}
