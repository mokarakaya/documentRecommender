package com.service;

import com.model.Document;
import com.model.DocumentTerm;
import com.model.SimilarDocumentDto;
import com.repository.DocumentRepository;
import com.repository.DocumentTermRepository;
import com.sree.textbytes.jtopia.Configuration;
import com.sree.textbytes.jtopia.TermDocument;
import com.sree.textbytes.jtopia.TermsExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.hibernate.Hibernate;
import org.hibernate.LobHelper;
import org.hibernate.engine.jdbc.LobCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by 212457624 on 5/2/2016.
 */

@Service
public class DocumentService {

    @Autowired
    SimilarityService similarityService;

    @Autowired
    DocumentTermRepository documentTermRepository;

    @Autowired
    DocumentRepository documentRepository;

    public void modelAppend(MultipartFile multiPartFile) throws IOException {
        Map<String, Double> terms = getTerms(multiPartFile);
        Document document= new Document();
        document.setFile(multiPartFile.getBytes());
        document.setName(multiPartFile.getOriginalFilename());
        documentRepository.save(document);
        DocumentTerm documentTerm= new DocumentTerm();
        documentTerm.setDocumentId(document.getId());
        documentTerm.setTerms(terms);
        documentTermRepository.save(documentTerm);

    }

    public Map<String, Double> getTerms(MultipartFile multiPartFile) throws IOException {
        PDDocument load = PDDocument.load(multiPartFile.getInputStream());
        PDFTextStripper stripper= new PDFTextStripper();
        String text = stripper.getText(load);
        text = text.replace(":", "");
        load.close();
        Configuration.setTaggerType("default");// "default" for lexicon POS
        // tagger and "openNLP" for
        // openNLP POS tagger
        Configuration.setSingleStrength(3);
        Configuration.setNoLimitStrength(2);
        // If tagger type is "default" , then set model location as
        // english-lexicon.txt"
        Configuration.setModelFileLocation("english-lexicon.txt");
        TermsExtractor termExtractor = new TermsExtractor();
        TermDocument termDocument = new TermDocument();
        termDocument = termExtractor.extractTerms(text);
        return normalizeTermVector(termDocument.getFinalFilteredTerms());
    }

    private Map<String, Double> normalizeTermVector(Map<String, ArrayList<Integer>> filteredTerm) {
        Iterator filteredTermMapIter = filteredTerm.entrySet().iterator();

        double norm = 0;
        while(filteredTermMapIter.hasNext()) {

            Map.Entry<String, ArrayList<Integer>> entry = (Map.Entry<String, ArrayList<Integer>>)filteredTermMapIter.next();
            norm += entry.getValue().get(0) * entry.getValue().get(0);

        }
        norm = Math.sqrt(norm);

        Map<String, Double> termVectorNorm = new HashMap<String, Double>();
        filteredTermMapIter = filteredTerm.entrySet().iterator();
        while(filteredTermMapIter.hasNext()) {
            Map.Entry<String, ArrayList<Integer>> entry = (Map.Entry<String, ArrayList<Integer>>)filteredTermMapIter.next();
            termVectorNorm.put(entry.getKey(), (entry.getValue().get(0).doubleValue() / norm));
        }

        return termVectorNorm;
    }

    public List<SimilarDocumentDto> getSimilarDocs(MultipartFile multiPartFile) throws IOException {
        List<SimilarDocumentDto> similarDocs= new ArrayList();
        Map<String, Double> terms = getTerms(multiPartFile);
        Iterable<DocumentTerm> documentTerms= documentTermRepository.findAll();
        for(DocumentTerm documentTerm: documentTerms){
            Map<String, Double> similarDocTerms = documentTerm.getTerms();
            double similarity=similarityService.getSimilarity(terms, similarDocTerms);
            Document document = documentRepository.findOne(documentTerm.getDocumentId());
            SimilarDocumentDto similarDocumentDto= new SimilarDocumentDto(document.getId(),document.getName(),similarity);
            similarDocs.add(similarDocumentDto);
        }
        return similarDocs.stream().sorted((object1, object2) -> object1.getSimilarity() > object2.getSimilarity() ? -1 : 1)
                .collect(Collectors.toList());
    }
}
