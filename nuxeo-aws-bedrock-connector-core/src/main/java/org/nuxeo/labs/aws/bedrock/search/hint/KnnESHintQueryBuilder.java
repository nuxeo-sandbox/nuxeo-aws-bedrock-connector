package org.nuxeo.labs.aws.bedrock.search.hint;

import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.elasticsearch.api.ESHintQueryBuilder;
import org.nuxeo.labs.aws.bedrock.search.os.KnnQueryBuilder;
import org.opensearch.common.lucene.search.function.FunctionScoreQuery;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.index.query.functionscore.ScoreFunctionBuilders;
import org.opensearch.index.query.functionscore.ScriptScoreFunctionBuilder;
import org.opensearch.script.Script;

import java.util.Arrays;
import java.util.Base64;

public class KnnESHintQueryBuilder implements ESHintQueryBuilder {

    @Override
    public QueryBuilder make(EsHint hint, String fieldName, Object value) {
        // In order to avoid this query to be embedded in a constant score query by the page provider,
        // the hint is must be tied to ecm:fulltext field in nxql and the value passed as a string of float seperated by a coma encode in base64
        String vectorString = new String(Base64.getDecoder().decode((String) value));

        double[] vector = Arrays.stream((vectorString).split(",")).mapToDouble(Double::parseDouble).toArray();

        KnnQueryBuilder knnQueryBuilder = new KnnQueryBuilder(fieldName, vector);

        //https://jira.nuxeo.com/browse/NXP-18835 workaround, add a function score offset
        // Add a custom function to adjust the score (e.g., multiply by 10)
        ScriptScoreFunctionBuilder scriptFunction = ScoreFunctionBuilders.scriptFunction(new Script("_score * 10"));

        FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(knnQueryBuilder,scriptFunction);
        functionScoreQueryBuilder.boost(1.0f);
        functionScoreQueryBuilder.maxBoost(10.0f);
        functionScoreQueryBuilder.scoreMode(FunctionScoreQuery.ScoreMode.SUM);

        return functionScoreQueryBuilder;
    }
}
