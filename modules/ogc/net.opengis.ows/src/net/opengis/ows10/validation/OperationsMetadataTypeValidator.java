/**
 *
 * $Id$
 */
package net.opengis.ows10.validation;

import net.opengis.ows10.DomainType;
import net.opengis.ows10.OperationType;
import org.eclipse.emf.common.util.EList;

/**
 * A sample validator interface for {@link net.opengis.ows10.OperationsMetadataType}.
 * This doesn't really do anything, and it's not a real EMF artifact.
 * It was generated by the org.eclipse.emf.examples.generator.validator plug-in to illustrate how EMF's code generator can be extended.
 * This can be disabled with -vmargs -Dorg.eclipse.emf.examples.generator.validator=false.
 */
public interface OperationsMetadataTypeValidator {
  boolean validate();

  boolean validateOperation(EList value);
  boolean validateParameter(EList value);
  boolean validateConstraint(EList value);
  boolean validateExtendedCapabilities(Object value);
}
