package biz.karms.sinkit.ejb.elastic;

import biz.karms.sinkit.exception.ArchiveException;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.SortBuilder;

import javax.ejb.Local;
import java.util.List;

/**
 * @author Michal Karm Babacek
 */
@Local
public interface ElasticService {
    <T extends Indexable> T getDocumentById(String id, String index, String type, Class<T> clazz) throws ArchiveException;

    <T extends Indexable> List<T> search(QueryBuilder query, SortBuilder sort, String index, String type, Class<T> clazz) throws ArchiveException;

    <T extends Indexable> List<T> search(QueryBuilder query, SortBuilder sort, String index, String type, int from, int size, Class<T> clazz) throws ArchiveException;

    <T extends Indexable> T index(T document, String index, String type) throws ArchiveException;

    boolean update(String documentId, Object updateDoc, String index, String type, Object upsertDoc) throws ArchiveException;

    <T extends Indexable> boolean delete(T document, String index, String type) throws ArchiveException;
}
