/**
 * OrthomclKeywordSearchPlugin -- text search using Oracle Text
 */
package org.orthomcl.wsfplugin;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eupathdb.websvccommon.wsfplugin.EuPathServiceException;
import org.eupathdb.websvccommon.wsfplugin.textsearch.AbstractOracleTextSearchPlugin;
import org.eupathdb.websvccommon.wsfplugin.textsearch.ResponseResultContainer;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.runtime.InstanceManager;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginRequest;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.PluginUserException;

/**
 * @author Steve and John I
 * @created Nov 2012
 */
public class KeywordSearchPlugin extends AbstractOracleTextSearchPlugin {

  private static final Logger logger = Logger.getLogger(KeywordSearchPlugin.class);

  protected WdkModel wdkModel;
  
  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wsf.WsfPlugin#execute(java.util.Map, java.lang.String[])
   */
  @Override
  public int execute(PluginRequest request, PluginResponse response) throws PluginModelException, PluginUserException  {
    logger.info("Invoking OrthomclKeywordSearchPlugin...");

    // get parameters
    Map<String, String> params = request.getParams();
    //String recordType = params.get(PARAM_WDK_RECORD_TYPE).trim().replaceAll(
    //    "'", "");
    String fields = params.get(PARAM_DATASETS).trim().replaceAll("'", "");
    logger.debug("fields = \"" + fields + "\"");
    String textExpression = params.get(PARAM_TEXT_EXPRESSION).trim();
    String detailTable = params.get(PARAM_DETAIL_TABLE).trim().replaceAll("'",
        "");
    String primaryKeyColumn = params.get(PARAM_PRIMARY_KEY_COLUMN).trim().replaceAll(
        "'", "");
    String projectId = params.get(PARAM_PROJECT_ID).trim().replaceAll("'", "");

    // get fields as quoted list
    StringBuffer quotedFields = new StringBuffer("");
    boolean notFirst = false;
    String[] ds = fields.split(",\\s*");
    for (String field : ds) {
      if (notFirst)
        quotedFields.append(",");
      quotedFields.append("'" + field + "'");
      notFirst = true;
    }

    String oracleTextExpression = transformQueryString(textExpression);
    ResponseResultContainer results = new ResponseResultContainer(response, request.getOrderedColumns());
    PreparedStatement ps = null;
    try {
      wdkModel = InstanceManager.getInstance(WdkModel.class, projectId);
      
      String sql = getQuery(detailTable, primaryKeyColumn, projectId, quotedFields.toString());
      ps = SqlUtils.getPreparedStatement(wdkModel.getAppDb().getDataSource(), sql, SqlUtils.Autocommit.OFF);
      logger.debug("oracleTextExpression = \"" + oracleTextExpression + "\"");
      ps.setString(1, oracleTextExpression);
      textSearch(results, ps, primaryKeyColumn, sql, "OrthoMclTextSearch");
      
      return 0;
    }
    catch (SQLException | EuPathServiceException ex) {
      throw new PluginModelException(ex);
    }
    finally {
      SqlUtils.closeStatement(ps);
    }
  }

private String getQuery(String detailTable, String primaryKeyColumn, String projectId, String fields) {

    String sql = new String(
        "select "
            + primaryKeyColumn
            + ",\n"
            + "       '"
            + projectId
            + "' as project_id, max(scoring) as max_score,\n"
            + "       apidb.tab_to_string(set(CAST(COLLECT(field_name) as apidb.varchartab)), ', ')\n"
            + "        as fields_matched\n" + "from (select  "
            + primaryKeyColumn + ", field_name, score(1) as scoring\n"
            + "      from " + detailTable + "\n"
            + "      where field_name in (" + fields + ")\n"
            + "        and contains(content, ?, 1) > 0)\n" + "group by  "
            + primaryKeyColumn + "\n" + "order by max(scoring) desc");
    logger.debug("SQL: " + sql);

    return sql;
  }

}
