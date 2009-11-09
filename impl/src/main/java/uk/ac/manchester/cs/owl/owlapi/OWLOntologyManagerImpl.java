package uk.ac.manchester.cs.owl.owlapi;

import org.semanticweb.owlapi.io.OWLOntologyInputSource;
import org.semanticweb.owlapi.io.OWLOntologyOutputTarget;
import org.semanticweb.owlapi.io.PhysicalURIInputSource;
import org.semanticweb.owlapi.io.PhysicalURIMappingNotFoundException;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.NonMappingOntologyIRIMapper;

import java.net.URI;
import java.util.*;
import java.util.logging.Logger;
/*
 * Copyright (C) 2006, University of Manchester
 *
 * Modifications to the initial code base are copyright of their
 * respective authors, or their employers as appropriate.  Authorship
 * of the modifications may be determined from the ChangeLog placed at
 * the end of this file.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */


/**
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 27-Oct-2006<br><br>
 */
public class OWLOntologyManagerImpl implements OWLOntologyManager, OWLOntologyFactory.OWLOntologyCreationHandler {

    private static final Logger logger = Logger.getLogger(OWLOntologyManagerImpl.class.getName());

    private Map<OWLOntologyID, OWLOntology> ontologiesByID;

    private Map<OWLOntologyID, URI> physicalURIsByID;

    private Map<OWLOntology, OWLOntologyFormat> ontologyFormatsByOntology;

    private Map<OWLImportsDeclaration, OWLOntologyID> ontologyIDsByImportsDeclaration;

    private List<OWLOntologyIRIMapper> documentMappers;

    private List<OWLOntologyFactory> ontologyFactories;

    private List<OWLOntologyStorer> ontologyStorers;

    private boolean broadcastChanges;

    private int loadCount = 0;

    private int importsLoadCount = 0;

    private boolean silentMissingImportsHandling;

    private OWLDataFactory dataFactory;

    private Map<OWLOntology, Set<OWLOntology>> importsClosureCache;

    private OWLOntologyManagerProperties properties;

    private List<MissingImportListener> missingImportsListeners;

    private List<OWLOntologyLoaderListener> loaderListeners;

    private List<OWLOntologyChangeProgressListener> progressListeners;

    private int autoGeneratedURICounter;

    private OWLOntologyChangeBroadcastStrategy defaultChangeBroadcastStrategy;


    public OWLOntologyManagerImpl(OWLDataFactory dataFactory) {
        this.dataFactory = dataFactory;
        properties = new OWLOntologyManagerProperties();
        ontologiesByID = new HashMap<OWLOntologyID, OWLOntology>();
        physicalURIsByID = new HashMap<OWLOntologyID, URI>();
        ontologyFormatsByOntology = new HashMap<OWLOntology, OWLOntologyFormat>();
        documentMappers = new ArrayList<OWLOntologyIRIMapper>();
        ontologyFactories = new ArrayList<OWLOntologyFactory>();
        ontologyIDsByImportsDeclaration = new HashMap<OWLImportsDeclaration, OWLOntologyID>();
        installDefaultURIMappers();
        installDefaultOntologyFactories();
        broadcastChanges = true;
        ontologyStorers = new ArrayList<OWLOntologyStorer>();
        importsClosureCache = new HashMap<OWLOntology, Set<OWLOntology>>();
        missingImportsListeners = new ArrayList<MissingImportListener>();
        loaderListeners = new ArrayList<OWLOntologyLoaderListener>();
        progressListeners = new ArrayList<OWLOntologyChangeProgressListener>();
        autoGeneratedURICounter = 0;
        defaultChangeBroadcastStrategy = new DefaultChangeBroadcastStrategy();
    }


    public OWLOntologyManagerProperties getProperties() {
        return properties;
    }


    public OWLDataFactory getOWLDataFactory() {
        return dataFactory;
    }


    public Set<OWLOntology> getOntologies() {
        return new HashSet<OWLOntology>(ontologiesByID.values());
    }


    public Set<OWLOntology> getOntologies(OWLAxiom axiom) {
        Set<OWLOntology> result = new HashSet<OWLOntology>(ontologiesByID.size());
        for (OWLOntology ont : getOntologies()) {
            if (ont.containsAxiom(axiom)) {
                result.add(ont);
            }
        }
        return result;
    }

    public boolean contains(OWLOntology ontology) {
        return ontologiesByID.containsValue(ontology);
    }


    public boolean contains(IRI ontologyIRI) {
        return contains(new OWLOntologyID(ontologyIRI));
    }

    public boolean contains(OWLOntologyID id) {
        return ontologiesByID.containsKey(id);
    }

    /**
     * Gets a previously loaded/created ontology that has the specified ontology IRI and no version IRI.
     * @param ontologyIRI The IRI of the ontology to be retrieved.
     * @return The ontology that has the specified IRI and no version IRI, or <code>null</code> if this manager does
     *         not manage an ontology with the specified IRI and no version IRI.
     */
    public OWLOntology getOntology(IRI ontologyIRI) {
        OWLOntologyID ontologyID = new OWLOntologyID(ontologyIRI);
        return getOntology(ontologyID);
    }

    /**
     * Gets a previously loaded/created ontology that has the specified ontology ID
     * @param ontologyID The ID of the ontology to retrieve
     * @return The ontology that has the specified ID, or <code>null</code> if this manager does not manage an ontology
     *         with the specified ontology ID.
     */
    public OWLOntology getOntology(OWLOntologyID ontologyID) {
        return ontologiesByID.get(ontologyID);

    }

    public Set<OWLOntology> getVersions(IRI ontology) {
        Set<OWLOntology> onts = new HashSet<OWLOntology>();
        for (OWLOntology ont : getOntologies()) {
            if (ontology.equals(ont.getOntologyID().getOntologyIRI())) {
                onts.add(ont);
            }
        }
        return onts;
    }

    /**
     * Given an imports declaration, obtains the ontology that this import has been resolved to.
     * @param declaration The declaration that points to the imported ontology.
     * @return The ontology that the imports declaration resolves to, or <code>null</code> if the imports declaration
     *         could not be resolved to an ontology, because the ontology was not loaded or has been removed from this
     *         manager
     */
    public OWLOntology getImportedOntology(OWLImportsDeclaration declaration) {
        OWLOntologyID ontologyID = ontologyIDsByImportsDeclaration.get(declaration);
        if (ontologyID == null) {
            // No such ontology
            return null;
        }
        else {
            return getOntology(ontologyID);
        }
    }

    /**
     * Gets the set of <em>loaded</em> ontologies that the specified ontology is related to via the directlyImports relation as
     * defined in Section 3.4 of the OWL 2 Structural specification
     * @param ontology The ontology whose direct imports are to be retrieved.
     * @return The set of <em>loaded</em> ontologies that the specified ontology is related to via the directlyImports
     *         relation.
     * @throws org.semanticweb.owlapi.model.UnknownOWLOntologyException
     *          if there isn't an ontology in this manager which has the specified IRI.
     */
    public Set<OWLOntology> getDirectImports(OWLOntology ontology) throws UnknownOWLOntologyException {
        if (!contains(ontology)) {
            throw new UnknownOWLOntologyException(ontology.getOntologyID());
        }
        Set<OWLOntology> imports = new HashSet<OWLOntology>();
        for (OWLImportsDeclaration axiom : ontology.getImportsDeclarations()) {
            OWLOntology importedOntology = getImportedOntology(axiom);
            if (importedOntology != null) {
                imports.add(importedOntology);
            }
        }
        return imports;
    }

    /**
     * Gets the set of ontologies that are in the transitive closure of the directly imports relation.
     * @param ontology The ontology whose imports are to be retrieved.
     * @return A set of <code>OWLOntology</code>ies that are in the transitive closure of the directly imports relation
     *         of this ontology. If, for what ever reason, an imported ontology could not be loaded, then it will not be contained in the
     *         returned set of ontologies.
     * @throws org.semanticweb.owlapi.model.UnknownOWLOntologyException
     *          if there isn't an ontology in this manager which has the specified IRI.
     */
    public Set<OWLOntology> getImports(OWLOntology ontology) throws UnknownOWLOntologyException {
        if (!contains(ontology)) {
            throw new UnknownOWLOntologyException(ontology.getOntologyID());
        }
        Set<OWLOntology> result = new HashSet<OWLOntology>();
        getImports(ontology, result);
        return result;
    }

    /**
     * A method that gets the imports of a given ontology
     * @param ont    The ontology whose (transitive) imports are to be retrieved.
     * @param result A place to store the result - the transitive closure of the imports will be stored in this
     *               result set.
     */
    private void getImports(OWLOntology ont, Set<OWLOntology> result) {
        for (OWLOntology directImport : getDirectImports(ont)) {
            if (result.add(directImport)) {
                getImports(directImport, result);
            }
        }
    }

    public Set<OWLOntology> getImportsClosure(OWLOntology ontology) {
        Set<OWLOntology> ontologies = importsClosureCache.get(ontology);
        if (ontologies == null) {
            ontologies = new HashSet<OWLOntology>();
            getImportsClosure(ontology, ontologies);
            importsClosureCache.put(ontology, ontologies);
        }
        return Collections.unmodifiableSet(ontologies);
    }


    /**
     * A recursive method that gets the reflexive transitive closure of the ontologies that are imported
     * by this ontology.
     * @param ontology   The ontology whose reflexive transitive closure is to be retrieved
     * @param ontologies a place to store the result
     */
    private void getImportsClosure(OWLOntology ontology, Set<OWLOntology> ontologies) {
        ontologies.add(ontology);
        for (OWLOntology ont : getDirectImports(ontology)) {
            if (!ontologies.contains(ont)) {
                getImportsClosure(ont, ontologies);
            }
        }
    }


    public List<OWLOntology> getSortedImportsClosure(OWLOntology ontology) throws UnknownOWLOntologyException {
        List<OWLOntology> importsClosure = new ArrayList<OWLOntology>();
        getSortedImportsClosure(ontology, importsClosure, new HashSet<OWLOntology>());
        return importsClosure;
    }

    private void getSortedImportsClosure(OWLOntology ontology, List<OWLOntology> imports, Set<OWLOntology> marker) {
        if (!marker.contains(ontology)) {
            imports.add(ontology);
            marker.add(ontology);
            for (OWLOntology imported : getDirectImports(ontology)) {
                getSortedImportsClosure(imported, imports, marker);
            }
        }
    }


    /**
     * Determines if a change is applicable.  A change may not be applicable
     * for a number of reasons.
     * @param change The change to be tested.
     * @return <code>true</code> if the change is applicable,
     *         otherwise, <code>false</code>.
     */
    private boolean isChangeApplicable(OWLOntologyChange change) {
        if (!getProperties().isLoadAnnotationAxioms() && change instanceof AddAxiom) {
            if (change.getAxiom() instanceof OWLAnnotationAxiom) {
                return false;
            }
        }
        return true;
    }


    /**
     * Applies a change to an ontology and performs the necessary housekeeping
     * tasks.
     * @param change The change to be applied.
     * @return A list of changes that were actually applied.
     * @throws OWLOntologyChangeException
     */
    private List<OWLOntologyChange> enactChangeApplication(OWLOntologyChange change) throws OWLOntologyChangeException {
        if (!isChangeApplicable(change)) {
            return Collections.emptyList();
        }
        OWLOntology ont = change.getOntology();
        if (!(ont instanceof OWLMutableOntology)) {
            throw new ImmutableOWLOntologyChangeException(change);
        }
        List<OWLOntologyChange> appliedChanges = ((OWLMutableOntology) ont).applyChange(change);
        checkForOntologyIDChange(change);
        checkForImportsChange(change);
        return appliedChanges;
    }


    public List<OWLOntologyChange> applyChanges(List<? extends OWLOntologyChange> changes) throws OWLOntologyChangeException {
        List<OWLOntologyChange> appliedChanges = new ArrayList<OWLOntologyChange>(changes.size() + 2);
        fireBeginChanges(changes.size());
        for (OWLOntologyChange change : changes) {
            appliedChanges.addAll(enactChangeApplication(change));
            fireChangeApplied(change);
        }
        fireEndChanges();
        broadcastChanges(changes);
        return appliedChanges;
    }


    public List<OWLOntologyChange> addAxiom(OWLOntology ont, OWLAxiom axiom) throws OWLOntologyChangeException {
        return addAxioms(ont, Collections.singleton(axiom));
    }


    public List<OWLOntologyChange> addAxioms(OWLOntology ont, Set<? extends OWLAxiom> axioms) throws OWLOntologyChangeException {
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>(axioms.size());
        // Optimisation - Precheck that the ontology is an immutable ontology.
        if (ont instanceof OWLMutableOntology) {
            fireBeginChanges(axioms.size());
            for (OWLAxiom ax : axioms) {
                // Further optimisation - precheck that the axiom isn't in the ontology.
                if (!ont.containsAxiom(ax)) {
                    AddAxiom addAx = new AddAxiom(ont, ax);
                    changes.addAll(enactChangeApplication(addAx));
                    fireChangeApplied(addAx);
                }
            }
            fireEndChanges();
        }
        broadcastChanges(changes);
        return changes;
    }

    public List<OWLOntologyChange> removeAxiom(OWLOntology ont, OWLAxiom axiom) throws OWLOntologyChangeException {
        return removeAxioms(ont, Collections.singleton(axiom));
    }

    public List<OWLOntologyChange> removeAxioms(OWLOntology ont, Set<? extends OWLAxiom> axioms) throws OWLOntologyChangeException {
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>(axioms.size());
        // Optimisation - Precheck that the ontology is an immutable ontology.
        if (ont instanceof OWLMutableOntology) {
            fireBeginChanges(axioms.size());
            for (OWLAxiom ax : axioms) {
                // Further optimisation - precheck that the axiom is in the ontology.
                if (ont.containsAxiom(ax)) {
                    RemoveAxiom removeAxiom = new RemoveAxiom(ont, ax);
                    changes.addAll(enactChangeApplication(removeAxiom));
                    fireChangeApplied(removeAxiom);
                }
            }
            fireEndChanges();
        }
        broadcastChanges(changes);
        return changes;
    }

    public List<OWLOntologyChange> applyChange(OWLOntologyChange change) throws OWLOntologyChangeException {
        fireBeginChanges(1);
        List<OWLOntologyChange> changes = enactChangeApplication(change);
        fireChangeApplied(change);
        fireEndChanges();
        broadcastChanges(changes);
        return changes;
    }


    private void checkForImportsChange(OWLOntologyChange change) {
        if (change.isImportChange()) {
            resetImportsClosureCache();
        }
    }


    private void checkForOntologyIDChange(OWLOntologyChange change) {
        if (change instanceof SetOntologyID) {
            SetOntologyID setURI = (SetOntologyID) change;
            renameOntology(setURI.getOriginalOntologyID());
            resetImportsClosureCache();
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Methods to create, load and reload ontologies
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////


    public void ontologyCreated(OWLOntology ontology) {
        // This method is called when a factory that we have asked to create or
        // load an ontology has created the ontology.  We add the ontology to the
        // set of loaded ontologies.
        addOntology(ontology);
    }


    /**
     * Sets the format of an ontology
     * @param ontology The ontology
     * @param format   The format of the ontology
     */
    public void setOntologyFormat(OWLOntology ontology, OWLOntologyFormat format) {
        ontologyFormatsByOntology.put(ontology, format);
    }


    public OWLOntologyFormat getOntologyFormat(OWLOntology ontology) {
        return ontologyFormatsByOntology.get(ontology);
    }

    private IRI toIRI(URI uri) {
        return dataFactory.getIRI(uri);
    }

    public OWLOntology createOntology() throws OWLOntologyCreationException {
        // Brand new ontology without a URI
        return createOntology(new OWLOntologyID());
    }

    public OWLOntology createOntology(IRI ontologyIRI) throws OWLOntologyCreationException {
        return createOntology(new OWLOntologyID(ontologyIRI));
    }

    public OWLOntology createOntology(IRI ontologyIRI, IRI versionIRI) throws OWLOntologyCreationException {
        return createOntology(new OWLOntologyID(ontologyIRI, versionIRI));
    }

    public OWLOntology createOntology(OWLOntologyID ontologyID) throws OWLOntologyCreationException {
        OWLOntology ontology = ontologiesByID.get(ontologyID);
        if (ontology != null) {
            return ontology;
        }
        URI physicalURI = getDocumentURI(ontologyID, false);
        for (OWLOntologyFactory factory : ontologyFactories) {
            if (factory.canCreateFromPhysicalURI(physicalURI)) {
                physicalURIsByID.put(ontologyID, physicalURI);
                return factory.createOWLOntology(ontologyID, physicalURI, this);
            }
        }
        throw new OWLOntologyFactoryNotFoundException(physicalURI);
    }

    public OWLOntology createOntology(IRI ontologyIRI, Set<OWLOntology> ontologies) throws OWLOntologyCreationException, OWLOntologyChangeException {
        return createOntology(ontologyIRI, ontologies, false);
    }


    public OWLOntology createOntology(IRI ontologyIRI, Set<OWLOntology> ontologies, boolean copyLogicalAxiomsOnly) throws OWLOntologyCreationException, OWLOntologyChangeException {
        if (contains(ontologyIRI)) {
            throw new OWLOntologyAlreadyExistsException(new OWLOntologyID(ontologyIRI));
        }
        OWLOntology ont = createOntology(ontologyIRI);
        Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
        for (OWLOntology ontology : ontologies) {
            if (copyLogicalAxiomsOnly) {
                axioms.addAll(ontology.getLogicalAxioms());
            }
            else {
                axioms.addAll(ontology.getAxioms());
            }
        }
        addAxioms(ont, axioms);
        return ont;
    }


    public OWLOntology createOntology(Set<OWLAxiom> axioms, IRI iri) throws OWLOntologyCreationException, OWLOntologyChangeException {
        if (contains(iri)) {
            throw new OWLOntologyAlreadyExistsException(new OWLOntologyID(iri));
        }
        OWLOntology ont = createOntology(iri);
        addAxioms(ont, axioms);
        return ont;
    }


    public OWLOntology createOntology(Set<OWLAxiom> axioms) throws OWLOntologyCreationException, OWLOntologyChangeException {
        return createOntology(axioms, getNextAutoGeneratedIRI());
    }

    protected IRI getNextAutoGeneratedIRI() {
        autoGeneratedURICounter = autoGeneratedURICounter + 1;
        return IRI.create("owlapi:ontology:ont" + autoGeneratedURICounter);
    }

    public OWLOntology loadOntology(IRI ontologyIRI) throws OWLOntologyCreationException {
        OWLOntologyID id = new OWLOntologyID(ontologyIRI);

        OWLOntology ontByID = ontologiesByID.get(id);
        if (ontByID != null) {
            return ontByID;
        }
        URI physicalURI = getDocumentURI(id, true);
        // The ontology might be being loaded, but its logical URI might
        // not have been set (as is probably the case with RDF/XML!)
        if (physicalURI != null) {
            OWLOntology ontByPhysicalURI = getOntology(IRI.create(physicalURI));
            if (ontByPhysicalURI != null) {
                return ontByPhysicalURI;
            }
        }
        else {
            // Nothing we can do here.  We can't get a physical URI to load
            // the ontology from.
            throw new PhysicalURIMappingNotFoundException(ontologyIRI);
        }
        return loadOntology(ontologyIRI, new PhysicalURIInputSource(physicalURI));
    }


    public OWLOntology loadOntologyFromPhysicalURI(URI uri) throws OWLOntologyCreationException {
        // Ontology URI not known in advance
        return loadOntology(null, new PhysicalURIInputSource(uri));
    }

    public OWLOntology loadOntology(OWLOntologyInputSource inputSource) throws OWLOntologyCreationException {
        // Ontology URI not known in advance
        return loadOntology(null, inputSource);
    }


    /**
     * This is the method that all the other load method delegate to.
     * @param ontologyIRI The URI of the ontology to be loaded.  This is only used to
     *                    report to listeners and may be <code>null</code>
     * @param inputSource The input source that specifies where the ontology should be loaded from.
     * @return The ontology that was loaded.
     * @throws OWLOntologyCreationException If the ontology could not be loaded.
     */
    protected OWLOntology loadOntology(IRI ontologyIRI, OWLOntologyInputSource inputSource) throws OWLOntologyCreationException {
        if (loadCount != importsLoadCount) {
            System.err.println("Runtime Warning: Parsers should load imported ontologies using the makeImportLoadRequest method.");
        }
        fireStartedLoadingEvent(new OWLOntologyID(ontologyIRI), inputSource.getPhysicalURI(), loadCount > 0);
        loadCount++;
        broadcastChanges = false;
        OWLOntologyCreationException ex = null;
        OWLOntologyID idOfLoadedOntology = new OWLOntologyID();
        try {
            for (OWLOntologyFactory factory : ontologyFactories) {
                if (factory.canLoad(inputSource)) {
                    // Note - there is no need to add the ontology here, because it will be added
                    // when the ontology is created.
                    OWLOntology ontology = factory.loadOWLOntology(inputSource, this);
                    idOfLoadedOntology = ontology.getOntologyID();
                    // Store the ontology to physical URI mapping
                    physicalURIsByID.put(ontology.getOntologyID(), inputSource.getPhysicalURI());
                    return ontology;
                }
            }
        }
        catch (OWLOntologyCreationException e) {
            ex = e;
            throw e;
        }
        finally {
            loadCount--;
            if (loadCount == 0) {
                broadcastChanges = true;
                // Completed loading ontology and imports
            }
            fireFinishedLoadingEvent(idOfLoadedOntology, inputSource.getPhysicalURI(), loadCount > 0, ex);
        }
        throw new OWLOntologyFactoryNotFoundException(inputSource.getPhysicalURI());
    }


    public void removeOntology(OWLOntology ontology) {
        ontologiesByID.remove(ontology.getOntologyID());
        ontologyFormatsByOntology.remove(ontology);
        physicalURIsByID.remove(ontology.getOntologyID());
        for (Iterator<OWLImportsDeclaration> it = ontologyIDsByImportsDeclaration.keySet().iterator(); it.hasNext();) {
            if (ontologyIDsByImportsDeclaration.get(it.next()).equals(ontology.getOntologyID())) {
                it.remove();
            }
        }
        resetImportsClosureCache();
    }

    private void addOntology(OWLOntology ont) {
        ontologiesByID.put(ont.getOntologyID(), ont);
    }


    public URI getPhysicalURIForOntology(OWLOntology ontology) throws UnknownOWLOntologyException {
        if (!contains(ontology)) {
            throw new UnknownOWLOntologyException(ontology.getOntologyID());
        }
        return physicalURIsByID.get(ontology.getOntologyID());
    }


    public void setPhysicalURIForOntology(OWLOntology ontology, URI physicalURI) throws UnknownOWLOntologyException {
        if (!ontologiesByID.containsKey(ontology.getOntologyID())) {
            throw new UnknownOWLOntologyException(ontology.getOntologyID());
        }
        physicalURIsByID.put(ontology.getOntologyID(), physicalURI);
    }


    /**
     * Handles a rename of an ontology.  This method should only be called *after* the change has been applied
     * @param oldID The original ID of the ontology
     */
    private void renameOntology(OWLOntologyID oldID) {
        OWLOntology ont = ontologiesByID.get(oldID);
        if (ont == null) {
            // Nothing to rename!
            return;
        }
        ontologiesByID.remove(oldID);
        ontologiesByID.put(ont.getOntologyID(), ont);
        URI physicalURI = physicalURIsByID.remove(oldID);
        physicalURIsByID.put(ont.getOntologyID(), physicalURI);
        resetImportsClosureCache();
    }


    private void resetImportsClosureCache() {
        importsClosureCache.clear();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Methods to save ontologies
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////


    public void saveOntology(OWLOntology ontology) throws OWLOntologyStorageException, UnknownOWLOntologyException {
        OWLOntologyFormat format = getOntologyFormat(ontology);
        saveOntology(ontology, format);
    }


    public void saveOntology(OWLOntology ontology, OWLOntologyFormat ontologyFormat) throws OWLOntologyStorageException, UnknownOWLOntologyException {
        URI physicalURI = getPhysicalURIForOntology(ontology);
        saveOntology(ontology, ontologyFormat, physicalURI);
    }


    public void saveOntology(OWLOntology ontology, URI physicalURI) throws OWLOntologyStorageException, UnknownOWLOntologyException {
        OWLOntologyFormat format = getOntologyFormat(ontology);
        saveOntology(ontology, format, physicalURI);
    }


    public void saveOntology(OWLOntology ontology, OWLOntologyFormat ontologyFormat, URI physcialURI) throws OWLOntologyStorageException, UnknownOWLOntologyException {
        for (OWLOntologyStorer storer : ontologyStorers) {
            if (storer.canStoreOntology(ontologyFormat)) {
                storer.storeOntology(this, ontology, physcialURI, ontologyFormat);
                return;
            }
        }
        throw new OWLOntologyStorerNotFoundException(ontologyFormat);
    }


    public void saveOntology(OWLOntology ontology, OWLOntologyOutputTarget outputTarget) throws OWLOntologyStorageException, UnknownOWLOntologyException {
        saveOntology(ontology, getOntologyFormat(ontology), outputTarget);
    }


    public void saveOntology(OWLOntology ontology, OWLOntologyFormat ontologyFormat, OWLOntologyOutputTarget outputTarget) throws OWLOntologyStorageException, UnknownOWLOntologyException {
        for (OWLOntologyStorer storer : ontologyStorers) {
            if (storer.canStoreOntology(ontologyFormat)) {
                storer.storeOntology(this, ontology, outputTarget, ontologyFormat);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Methods to add/remove ontology storers
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////


    public void addOntologyStorer(OWLOntologyStorer storer) {
        ontologyStorers.add(0, storer);
    }


    public void removeOntologyStorer(OWLOntologyStorer storer) {
        ontologyStorers.remove(storer);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Methods to add/remove mappers etc.
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////


    public void addIRIMapper(OWLOntologyIRIMapper mapper) {
        documentMappers.add(0, mapper);
    }


    public void clearIRIMappers() {
        documentMappers.clear();
    }


    public void removeIRIMapper(OWLOntologyIRIMapper mapper) {
        documentMappers.remove(mapper);
    }


    public void addOntologyFactory(OWLOntologyFactory factory) {
        ontologyFactories.add(0, factory);
        factory.setOWLOntologyManager(this);
    }


    public void removeOntologyFactory(OWLOntologyFactory factory) {
        ontologyFactories.remove(factory);
    }


    /**
     * Uses the mapper mechanism to obtain a physical URI for an ontology
     * URI.
     * @param ontologyID The ontology ID for which the physical mapping is to be retrieved
     * @param quiet      If set to <code>true</code> and a mapping can't be found then a value of <code>null</code>
     *                   is returned.  If set to <code>false</code> and a mapping can't be found then an exception OWLOntologyURIMappingNotFoundException
     *                   is thrown.
     * @return The physical URI that corresponds to the ontology URI, or
     *         <code>null</code> if no physical URI can be found.
     */
    private URI getDocumentURI(OWLOntologyID ontologyID, boolean quiet) {
        for (OWLOntologyIRIMapper mapper : documentMappers) {
            IRI defIRI = ontologyID.getDefaultDocumentIRI();
            if (defIRI == null) {
                return null;
            }
            URI physicalURI = mapper.getPhysicalURI(defIRI);
            if (physicalURI != null) {
                return physicalURI;
            }
        }
        if (!quiet) {
            throw new OWLOntologyURIMappingNotFoundException(ontologyID.getDefaultDocumentIRI().toURI());
        }
        else {
            return null;
        }
    }


    private void installDefaultURIMappers() {
        // By defaut install the default mapper that simply maps
        // ontology URIs to themselves.
        addIRIMapper(new NonMappingOntologyIRIMapper());
    }


    private void installDefaultOntologyFactories() {
        // The default factories are the ones that can load
        // ontologies from http:// and file:// URIs
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Listener stuff - methods to add/remove listeners
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    private Map<OWLOntologyChangeListener, OWLOntologyChangeBroadcastStrategy> listenerMap = new LinkedHashMap<OWLOntologyChangeListener, OWLOntologyChangeBroadcastStrategy>();


    public void addOntologyChangeListener(OWLOntologyChangeListener listener) {
        listenerMap.put(listener, defaultChangeBroadcastStrategy);
    }

//    public void addAxiomClosureChangeListener(OWLOntology ontology, OWLAxiomClosureChangeListener listener) {
//        List<OWLAxiomClosureChangeListener> listeners = axiomClosureChangeListeners.get(ontology);
//        if (listeners == null) {
//            listeners = new ArrayList<OWLAxiomClosureChangeListener>();
//        }
//        listeners.add(listener);
//    }
//
//    public void removeAxiomClosureChangeListener(OWLOntology ontology, OWLAxiomClosureChangeListener listener) {
//        List<OWLAxiomClosureChangeListener> listeners = axiomClosureChangeListeners.get(ontology);
//        if (listeners != null) {
//            listeners.remove(listener);
//            if (listeners.isEmpty()) {
//                axiomClosureChangeListeners.remove(ontology);
//            }
//        }
//    }

    /**
     * Broadcasts to attached listeners, using the various broadcasting
     * strategies that were specified for each listener.
     * @param changes The ontology changes to broadcast
     */
    private void broadcastChanges(List<? extends OWLOntologyChange> changes) {
        if (!broadcastChanges) {
            return;
        }
        for (OWLOntologyChangeListener listener : new ArrayList<OWLOntologyChangeListener>(listenerMap.keySet())) {
            OWLOntologyChangeBroadcastStrategy strategy = listenerMap.get(listener);
            if (strategy == null) {
                // This listener may have been removed during the broadcast of the changes,
                // so when we attempt to retrieve it from the map it isn't there (because
                // we iterate over a copy).
                continue;
            }
            try {
                // Handle exceptions on a per listener basis.  If we have
                // badly behaving listeners, we don't want one listener
                // to prevent the other listeners from receiving events.
                strategy.broadcastChanges(listener, changes);
            }
            catch (Throwable e) {
                logger.warning("BADLY BEHAVING LISTENER: " + e);
                e.printStackTrace();
            }
        }
    }

    public void setDefaultChangeBroadcastStrategy(OWLOntologyChangeBroadcastStrategy strategy) {
        if (strategy != null) {
            defaultChangeBroadcastStrategy = strategy;
        }
        else {
            defaultChangeBroadcastStrategy = new DefaultChangeBroadcastStrategy();
        }
    }

    public void addOntologyChangeListener(OWLOntologyChangeListener listener, OWLOntologyChangeBroadcastStrategy strategy) {
        listenerMap.put(listener, strategy);
    }


    public void removeOntologyChangeListener(OWLOntologyChangeListener listener) {
        listenerMap.remove(listener);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Imports etc.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private OWLOntology loadImports(OWLImportsDeclaration declaration) throws OWLOntologyCreationException {
        importsLoadCount++;
        OWLOntology ont = null;
        try {
            ont = loadOntology(declaration.getIRI());
        }
        catch (OWLOntologyCreationException e) {
            if (!silentMissingImportsHandling) {
                throw e;
            }
            else {
                // Silent
                MissingImportEvent evt = new MissingImportEvent(declaration.getURI(), e);
                fireMissingImportEvent(evt);
            }
        }
        finally {
            importsLoadCount--;
        }
        return ont;
    }


    public void makeLoadImportRequest(OWLImportsDeclaration declaration) throws OWLOntologyCreationException {
        OWLOntology ont = loadImports(declaration);
        if (ont != null) {
            ontologyIDsByImportsDeclaration.put(declaration, ont.getOntologyID());
        }
    }


    public void setSilentMissingImportsHandling(boolean b) {
        silentMissingImportsHandling = b;
    }


    public boolean isSilentMissingImportsHandling() {
        return silentMissingImportsHandling;
    }


    public void addMissingImportListener(MissingImportListener listener) {
        missingImportsListeners.add(listener);
    }


    public void removeMissingImportListener(MissingImportListener listener) {
        missingImportsListeners.remove(listener);
    }

    protected void fireMissingImportEvent(MissingImportEvent evt) {
        for (MissingImportListener listener : new ArrayList<MissingImportListener>(missingImportsListeners)) {
            listener.importMissing(evt);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Other listeners etc.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////


    public void addOntologyLoaderListener(OWLOntologyLoaderListener listener) {
        loaderListeners.add(listener);
    }


    public void removeOntologyLoaderListener(OWLOntologyLoaderListener listener) {
        loaderListeners.remove(listener);
    }

    protected void fireStartedLoadingEvent(OWLOntologyID ontologyID, URI physicalURI, boolean imported) {
        for (OWLOntologyLoaderListener listener : new ArrayList<OWLOntologyLoaderListener>(loaderListeners)) {
            listener.startedLoadingOntology(new OWLOntologyLoaderListener.LoadingStartedEvent(ontologyID, physicalURI, imported));
        }
    }

    protected void fireFinishedLoadingEvent(OWLOntologyID ontologyID, URI physicalURI, boolean imported, OWLOntologyCreationException ex) {
        for (OWLOntologyLoaderListener listener : new ArrayList<OWLOntologyLoaderListener>(loaderListeners)) {
            listener.finishedLoadingOntology(new OWLOntologyLoaderListener.LoadingFinishedEvent(ontologyID, physicalURI, imported, ex));
        }
    }

    public void addOntologyChangeProgessListener(OWLOntologyChangeProgressListener listener) {
        progressListeners.add(listener);
    }

    public void removeOntologyChangeProgessListener(OWLOntologyChangeProgressListener listener) {
        progressListeners.remove(listener);
    }

    protected void fireBeginChanges(int size) {
        try {
            if (!broadcastChanges) {
                return;
            }
            for (OWLOntologyChangeProgressListener lsnr : progressListeners) {
                lsnr.begin(size);
            }
        }
        catch (Throwable e) {
            e.printStackTrace();
        }
    }

    protected void fireEndChanges() {
        try {
            if (!broadcastChanges) {
                return;
            }
            for (OWLOntologyChangeProgressListener lsnr : progressListeners) {
                lsnr.end();
            }
        }
        catch (Throwable e) {
            // Listener threw an exception
            e.printStackTrace();
        }
    }

    protected void fireChangeApplied(OWLOntologyChange change) {
        try {
            if (!broadcastChanges) {
                return;
            }
            if (progressListeners.isEmpty()) {
                return;
            }
            for (OWLOntologyChangeProgressListener lsnr : progressListeners) {
                lsnr.appliedChange(change);
            }
        }
        catch (Throwable e) {
            // Listener threw an exception
            e.printStackTrace();
        }
    }

}

