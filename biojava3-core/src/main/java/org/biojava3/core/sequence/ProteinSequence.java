/*
 *                    BioJava development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the individual
 * authors.  These should be listed in @author doc comments.
 *
 * For more information on the BioJava project and its aims,
 * or to join the biojava-l mailing list, visit the home page
 * at:
 *
 *      http://www.biojava.org/
 *
 * Created on DATE
 *
 */
package org.biojava3.core.sequence;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.biojava3.core.exceptions.CompoundNotFoundException;
import org.biojava3.core.sequence.compound.AmbiguityDNACompoundSet;
import org.biojava3.core.sequence.compound.AminoAcidCompound;
import org.biojava3.core.sequence.compound.AminoAcidCompoundSet;
import org.biojava3.core.sequence.compound.DNACompoundSet;
import org.biojava3.core.sequence.compound.NucleotideCompound;

import static org.biojava3.core.sequence.features.AbstractFeature.TYPE;

import org.biojava3.core.sequence.features.FeatureInterface;
import org.biojava3.core.sequence.io.DNASequenceCreator;
import org.biojava3.core.sequence.io.FastaReader;
import org.biojava3.core.sequence.io.PlainFastaHeaderParser;
import org.biojava3.core.sequence.loader.StringProxySequenceReader;
import org.biojava3.core.sequence.location.InsdcParser;
import org.biojava3.core.sequence.location.template.Location;
import org.biojava3.core.sequence.template.AbstractSequence;
import org.biojava3.core.sequence.template.CompoundSet;
import org.biojava3.core.sequence.template.ProxySequenceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The representation of a ProteinSequence
 *
 * @author Scooter Willis
 */
public class ProteinSequence extends AbstractSequence<AminoAcidCompound> {

	private final static Logger logger = LoggerFactory.getLogger(ProteinSequence.class);

    private ArrayList<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features
            = new ArrayList<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>>();
    private LinkedHashMap<String, ArrayList<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>>> groupedFeatures
            = new LinkedHashMap<String, ArrayList<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>>>();

    /**
     * Create a protein from a string
     *
     * @param seqString
     * @throws CompoundNotFoundException 
     */
    public ProteinSequence(String seqString) throws CompoundNotFoundException { 
        this(seqString, AminoAcidCompoundSet.getAminoAcidCompoundSet());
    }

    /**
     * Create a protein from a string with a user defined set of amino acids
     *
     * @param seqString
     * @param compoundSet
     * @throws CompoundNotFoundException 
     */
    public ProteinSequence(String seqString, CompoundSet<AminoAcidCompound> compoundSet) throws CompoundNotFoundException {
        super(seqString, compoundSet);
    }

    /**
     * A protein sequence where the storage of the sequence is somewhere else.
     * Could be loaded from a large Fasta file or via a Uniprot Proxy reader via
     * Uniprot ID
     *
     * @param proxyLoader
     */
    public ProteinSequence(ProxySequenceReader<AminoAcidCompound> proxyLoader) {
        this(proxyLoader, AminoAcidCompoundSet.getAminoAcidCompoundSet());
    }

    /**
     * A protein sequence where the storage of the sequence is somewhere else
     * with user defined set of amino acids. Could be loaded from a large Fasta
     * file or via a Uniprot Proxy reader via Uniprot ID
     *
     * @param proxyLoader
     */
    public ProteinSequence(ProxySequenceReader<AminoAcidCompound> proxyLoader, CompoundSet<AminoAcidCompound> compoundSet) {
        super(proxyLoader, compoundSet);
    }

    /**
     * A Protein sequence can be stand alone or loaded from a transcript
     * sequence. The design goal is to allow the creation of a Protein sequence
     * from a Uniprot ID or some other Protein ID that based on cross reference
     * you should be able to get the GeneSequence that codes for the protein if
     * the CDS/Gene region is known. From the GeneSequence you should then be
     * able to get the ChromosomeSequence which then allows you explore flaning
     * regions of the gene sequences. The framework is in place to do this but
     * currently hasn't been implement in the reverse direction starting from
     * the Protein sequence.
     *
     * @param parentDNASequence
     * @param begin
     * @param end
     */
    //TODO - Someone needs to check if this is a bug.  Shouldn't a parentDNASequence be something other then AminoAcid?
    //However, due to the derivation of this class, this is the only possible type argument for this parameter...
    public void setParentDNASequence(AbstractSequence<NucleotideCompound> parentDNASequence, Integer begin, Integer end) {
        this.setParentSequence(parentDNASequence);
        setBioBegin(begin);
        setBioEnd(end);
    }

    @Override
    public void addFeature(FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature) {
        features.add(feature);
        ArrayList<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> featureList = groupedFeatures.get(feature.getType());
        if (featureList == null) {
            featureList = new ArrayList<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>>();
            groupedFeatures.put(feature.getType(), featureList);
        }
        featureList.add(feature);
        Collections.sort(features, TYPE);
        Collections.sort(featureList, TYPE);

        // if feature is called 'coded_by' than add parent DNA location
        if (feature.getType().equals("coded_by")) {
            InsdcParser parser = new InsdcParser(DataSource.GENBANK);

            Location location = parser.parse(feature.getSource());
            // convert location into DNASequence
            try {
            	DNASequence dnaSeq = new DNASequence(getSequence(location), DNACompoundSet.getDNACompoundSet());
            	setParentDNASequence(dnaSeq, location.getStart().getPosition(), location.getEnd().getPosition());
            } catch (CompoundNotFoundException e) {
            	// TODO is there another solution to handle this exception?
            	logger.error("Could not add 'coded_by' parent DNA location feature, unrecognised compounds found in DNA sequence: {}",e.getMessage());
            }
        }
    }

    @Override
    public List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> getFeaturesByType(String type) {
        List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features = groupedFeatures.get(type);
        if (features == null) {
            features = new ArrayList<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>>();
        }
        return features;
    }

    @Override
    public List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> getFeatures() {
        return features;
    }
    
    private DNASequence getRawParentSequence(String accessId) throws IOException {
        String seqUrlTemplate = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=nuccore&id=%s&rettype=fasta&retmode=text";
        URL url = new URL(String.format(seqUrlTemplate, accessId));
        
        logger.info("Getting parent DNA sequence from URL: {}", url.toString());
        
        InputStream is = url.openConnection().getInputStream();

        FastaReader<DNASequence, NucleotideCompound> parentReader
                = new FastaReader<DNASequence, NucleotideCompound>(is,
                        new PlainFastaHeaderParser<DNASequence, NucleotideCompound>(),
                        new DNASequenceCreator(AmbiguityDNACompoundSet.getDNACompoundSet()));
        LinkedHashMap<String, DNASequence> seq = parentReader.process();

        DNASequence parentSeq = null;
        if (seq.size() == 1) {
            parentSeq = seq.values().iterator().next();
        }
        is.close();

        return parentSeq;
    }

    private String getSequence(Location cdna) {
        DNASequence rawParent = null;
        if (!cdna.isComplex()) {
            try {
                rawParent = getRawParentSequence(cdna.getAccession().getID());
                return cdna.getSubSequence(rawParent).getSequenceAsString();
            } catch (IOException e) {
                // return null
            	logger.error("Caught IOException when getting DNA sequence for id {}. Error: {}", cdna.getAccession().getID(), e.getMessage());
                return null;
            }
        } else {
            // in case of complex
            StringBuilder sb = new StringBuilder();

            for (Location sub : cdna.getSubLocations()) {
                String sebStr = getSequence(sub);
                sb.append((sebStr == null ? "" : sebStr));
            }

            return sb.toString();
        }
    }

    public static void main(String[] args) throws Exception {
        ProteinSequence proteinSequence = new ProteinSequence("ARNDCEQGHILKMFPSTWYVBZJX");
        logger.info("Protein Sequence: {}", proteinSequence.toString());

        StringProxySequenceReader<AminoAcidCompound> sequenceStringProxyLoader = new StringProxySequenceReader<AminoAcidCompound>("XRNDCEQGHILKMFPSTWYVBZJA", AminoAcidCompoundSet.getAminoAcidCompoundSet());
        ProteinSequence proteinSequenceFromProxy = new ProteinSequence(sequenceStringProxyLoader);
        logger.info("Protein Sequence from Proxy: {}", proteinSequenceFromProxy.toString());

    }
}
