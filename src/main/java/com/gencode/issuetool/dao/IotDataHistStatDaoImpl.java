/**=========================================================================================
<overview>센서장비현황 집계 DAO 처리현황
  </overview>
==========================================================================================*/
package com.gencode.issuetool.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import com.gencode.issuetool.etc.Constant;
import com.gencode.issuetool.etc.ObjMapper;
import com.gencode.issuetool.etc.Utils;
import com.gencode.issuetool.io.PageRequest;
import com.gencode.issuetool.io.PageResultObj;
import com.gencode.issuetool.io.SearchMapObj;
import com.gencode.issuetool.io.StatsGenTimeMode;
import com.gencode.issuetool.io.TimeMode;
import com.gencode.issuetool.obj.IotData;
import com.gencode.issuetool.obj.IotDataHistStat;

/**
 * 1. 5초단원 조회내역 일괄 입력
 * 2. 최근 1분, 10분, 1시간, 1일 데이터 조회(key: plantNo/plantPartCode/facilCode)
 * 1. 1분/10분/1시간/6시간/1일단위 평균값/최저값/최고값집계
 * @author jinno
 *
 */

@Component("IotDataHistStatDao")
public class IotDataHistStatDaoImpl extends AbstractDaoImpl implements IotDataHistStatDao {

	final String fields= "created_dtm,time_mode,interior_code,device_id,avg_humid_val,avg_smoke_val,avg_temp_val,avg_co_val,min_humid_val,min_smoke_val,min_temp_val,min_co_val,max_humid_val,max_smoke_val,max_temp_val,max_co_val,flame_cnt";
	final String fieldsRealChartAvg= "created_dtm,time_mode,interior_code,device_id,avg_humid_val humid_val,avg_smoke_val smoke_val,avg_temp_val temp_val,avg_co_val co_val,flame_cnt flame";
	final String fieldsRealChartMax= "created_dtm,time_mode,interior_code,device_id,min_humid_val humid_val,min_smoke_val smoke_val,min_temp_val temp_val,min_co_val co_val,flame_cnt flame";
	final String fieldsRealChartMin= "created_dtm,time_mode,interior_code,device_id,max_humid_val humid_val,max_smoke_val smoke_val,max_temp_val temp_val,max_co_val co_val,flame_cnt flame";
	@Value("${chart-data-array-size:30}") int chartDataArraySize;

	
	public IotDataHistStatDaoImpl(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
		super(jdbcTemplate, namedParameterJdbcTemplate);
	}

	@Override
	public long register(IotDataHistStat t) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		namedParameterJdbcTemplate.update("INSERT INTO iot_data_hist_stat (created_dtm,time_mode,interior_code,device_id,avg_humid_val,avg_smoke_val,avg_temp_val,avg_co_val,min_humid_val,min_smoke_val,min_temp_val,min_co_val,max_humid_val,max_smoke_val,max_temp_val,max_co_val,flame_cnt) " + 
				"VALUES(:createdDtm,:timeMode,:interiorCode,:deviceId,:avgHumidVal,:avgSmokeVal,:avgTempVal,:avgCoVal,:minHumidVal,:minSmokeVal,:minTempVal,:minCoVal,:maxHumidVal,:maxSmokeVal,:maxTempVal,:maxCoVal,:flameCnt)"
				,new BeanPropertySqlParameterSource(t), keyHolder);
		return (long) keyHolder.getKey().longValue();
	}
	
	@Override
	public int[] register(List<IotDataHistStat> t) {
		throw new UnsupportedOperationException();		
	}

	@Override
	public Optional<IotDataHistStat> load(long id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long delete(long id) {
		throw new UnsupportedOperationException();
	}
	@Override
	public long update(IotDataHistStat t) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Optional<List<IotDataHistStat>> loadAll() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Optional<List<IotDataHistStat>> search(Map<String, String> map) {
		SearchMapObj searchMapObj = new SearchMapObj(map);
		List<IotDataHistStat> t = namedParameterJdbcTemplate.query
				("select "+fields+" from iot_data_hist_stat where 1=1"
						+ searchMapObj.andQuery()
						, searchMapObj.params()
						, new BeanPropertyRowMapper<IotDataHistStat>(IotDataHistStat.class));
		return Optional.of(t);
	}

	@Override
	public Optional<PageResultObj<List<IotDataHistStat>>> search(PageRequest req) {
		String queryStr = "select "+fields+" from iot_data_hist_stat where 1=1";
		return internalSearch(queryStr, req, IotDataHistStat.class);
	}
	/**
	 * 최종건 삭제 후 재생성하기 위해 최종집계생성시간 리턴
	 * 최종건 없으면 삭제 스킵하도록 현재시간-1 리턴 
	 */
	public String getPreGenFromDtm(String timeMode) {
		StatsGenTimeMode statsGenTimeMode = new StatsGenTimeMode(timeMode);
		return jdbcTemplate.queryForObject("select ifnull(max(created_dtm), date_format(date_sub(now(), interval "+statsGenTimeMode.getStrTime()+"), '%Y-%m-%d "
							+statsGenTimeMode.getStrDateFrmt()+"')) from iot_data_hist_stat where time_mode = '"+timeMode+"'", String.class);
			
	}
	
	int cleansePreviousDataGen(String createdDtm, String timeMode) {
		//String createdDtm = getPreGenFromDtm("1M");
		return namedParameterJdbcTemplate.update("delete from iot_data_hist_stat " + 
				"where created_dtm >= :createdDtm " + 
				"and time_mode = :timeMode",
				new MapSqlParameterSource() {{
					addValue("createdDtm",createdDtm);
					addValue("timeMode",timeMode);
				}});
	}

	@Override
	public String getCreatedDtmPreMinuteDataGen() {
		return getPreGenFromDtm(Constant.DASHBOARD_STATS_TIME_MODE_1MINUTE.get());
	}
	
	/** 4. 분단위 등록
		4.1. 분단위 등록시 기존등록 삭제
		hst테이블=>stat테이블
	 */
	@Override
	public int cleansePreMinuteDataGen(String createdDtm) {
		//String createdDtm = getPreGenFromDtm("1M");
		return cleansePreviousDataGen(createdDtm, Constant.DASHBOARD_STATS_TIME_MODE_1MINUTE.get());
	}
	/** 4. 분단위 등록
		4.2. 최종분이후 현재분 등록
		hst테이블=>stat테이블
	 */
	@Override
	public int generateMinuteData(String createdDtm) {
		return namedParameterJdbcTemplate.update("insert into iot_data_hist_stat\r\n" + 
				"select substr(created_dtm, 1,16) created_dtm , '1M', interior_code,device_id,\r\n" + 
				"	min(humid_val) min_humid_val,\r\n" + 
				"	avg(humid_val) avg_humid_val,\r\n" + 
				"	max(humid_val) max_humid_val,\r\n" + 
				"	min(smoke_val) min_smoke_val,\r\n" + 
				"	avg(smoke_val) avg_smoke_val,\r\n" + 
				"	max(smoke_val) max_smoke_val,\r\n" + 
				"	min(temp_val) min_temp_val,\r\n" + 
				"	avg(temp_val) avg_temp_val,\r\n" + 
				"	max(temp_val) max_temp_val,\r\n" + 
				"	min(co_val) min_co_val,\r\n" + 
				"	avg(co_val) avg_co_val,\r\n" + 
				"	max(co_val) max_co_val,\r\n" + 
				"	sum(flame) flame_cnt " + 
				"from iot_data_hist\r\n" + 
				"where created_dtm >= :createdDtm \r\n" + 
				"group by substr(created_dtm, 1,16), interior_code,device_id",
				new MapSqlParameterSource() {{
					addValue("createdDtm",createdDtm);
				}});
	}
	@Override
	public String getCreatedDtmPreHourlyDataGen() {
		return getPreGenFromDtm(Constant.DASHBOARD_STATS_TIME_MODE_1HOUR.get());
	}
	
	/**
	 * 5. 시간단위 등록
		5.1. 시간단위 등록시 기존등록 삭제
		stat테이블=>stat테이블
	 */
	@Override
	public int cleansePreHourlyDataGen(String createdDtm) {
		return cleansePreviousDataGen(createdDtm, Constant.DASHBOARD_STATS_TIME_MODE_1HOUR.get());
	}
	/**
	 * 5. 시간단위 등록
		5.2. 최종시간이후 현재시간 등록
		stat테이블=>stat테이블
	 */
	@Override
	public int generateHourlyData(String createdDtm) {
		return namedParameterJdbcTemplate.update("insert into iot_data_hist_stat\r\n" + 
				"select concat(substr(created_dtm, 1,13),':00') created_dtm , '1H', interior_code,device_id,\r\n" + 
				"	min(min_humid_val) min_humid_val,\r\n" + 
				"	avg(avg_humid_val) avg_humid_val,\r\n" + 
				"	max(max_humid_val) max_humid_val,\r\n" + 
				"	min(min_smoke_val) min_smoke_val,\r\n" + 
				"	avg(avg_smoke_val) avg_smoke_val,\r\n" + 
				"	max(max_smoke_val) max_smoke_val,\r\n" + 
				"	min(min_temp_val) min_temp_val,\r\n" + 
				"	avg(avg_temp_val) avg_temp_val,\r\n" + 
				"	max(max_temp_val) max_temp_val,\r\n" + 
				"	min(min_co_val) min_co_val,\r\n" + 
				"	avg(avg_co_val) avg_co_val,\r\n" + 
				"	max(max_co_val) max_co_val,\r\n" + 
				"	sum(flame_cnt) flame_cnt\r\n" + 
				"from iot_data_hist_stat\r\n" + 
				"where created_dtm >= :createdDtm \r\n" + 
				"and time_mode='1M'\r\n" + 
				"group by concat(substr(created_dtm, 1,13),':00'), interior_code,device_id",
				new MapSqlParameterSource() {{
					addValue("createdDtm",createdDtm);
				}});
	}
	@Override
	public String getCreatedDtmPre6HourlyDataGen() {
		return getPreGenFromDtm(Constant.DASHBOARD_STATS_TIME_MODE_6HOUR.get());
	}
	
	/**
	 * 5. 시간단위 등록
		5.1. 시간단위 등록시 기존등록 삭제
		stat테이블=>stat테이블
	 */
	@Override
	public int cleansePre6HourlyDataGen(String createdDtm) {
		return cleansePreviousDataGen(createdDtm, Constant.DASHBOARD_STATS_TIME_MODE_6HOUR.get());
	}
	/**
	 * 5. 시간단위 등록
		5.2. 최종시간이후 현재시간 등록
		stat테이블=>stat테이블
	 */
	@Override
	public int generate6HourlyData(String createdDtm) {
		return namedParameterJdbcTemplate.update("insert into iot_data_hist_stat\r\n" + 
				"select substr(date(substr(created_dtm,1,10))+interval 6 * (hour(created_dtm) div 6) hour,1,16) created_dtm , '6H', interior_code,device_id,\r\n" + 
				"	min(min_humid_val) min_humid_val,\r\n" + 
				"	avg(avg_humid_val) avg_humid_val,\r\n" + 
				"	max(max_humid_val) max_humid_val,\r\n" + 
				"	min(min_smoke_val) min_smoke_val,\r\n" + 
				"	avg(avg_smoke_val) avg_smoke_val,\r\n" + 
				"	max(max_smoke_val) max_smoke_val,\r\n" + 
				"	min(min_temp_val) min_temp_val,\r\n" + 
				"	avg(avg_temp_val) avg_temp_val,\r\n" + 
				"	max(max_temp_val) max_temp_val,\r\n" + 
				"	min(min_co_val) min_co_val,\r\n" + 
				"	avg(avg_co_val) avg_co_val,\r\n" + 
				"	max(max_co_val) max_co_val,\r\n" + 
				"	sum(flame_cnt) flame_cnt\r\n" + 
				"from iot_data_hist_stat\r\n" + 
				"where created_dtm >= :createdDtm \r\n" + 
				"and time_mode='1H'\r\n" + 
				"group by substr(date(substr(created_dtm,1,10))+interval 6 * (hour(created_dtm) div 6) hour,1,16), interior_code,device_id",
				new MapSqlParameterSource() {{
					addValue("createdDtm",createdDtm);
				}});
	}
	@Override
	public String getCreatedDtmPreDailyDataGen() {
		return getPreGenFromDtm(Constant.DASHBOARD_STATS_TIME_MODE_1DAY.get());
	}
	/**
	 * 6. 일단위 등록
		6.1. 일단위 등록시 기존등록 삭제
	 */
	@Override
	public int cleansePreDailyDataGen(String createdDtm) {
		return cleansePreviousDataGen(createdDtm, Constant.DASHBOARD_STATS_TIME_MODE_1DAY.get());
	}
	/**
	 * 6. 일단위 등록
		6.2.최종일자 이후 오늘자 등록
		stat테이블=>stat테이블
	 */
	@Override
	public int generateDailyData(String createdDtm) {
		return namedParameterJdbcTemplate.update("insert into iot_data_hist_stat\r\n" + 
				"select concat(substr(created_dtm, 1,10),' 00:00') created_dtm , '1D', interior_code,device_id,\r\n" + 
				"	min(min_humid_val) min_humid_val,\r\n" + 
				"	avg(avg_humid_val) avg_humid_val,\r\n" + 
				"	max(max_humid_val) max_humid_val,\r\n" + 
				"	min(min_smoke_val) min_smoke_val,\r\n" + 
				"	avg(avg_smoke_val) avg_smoke_val,\r\n" + 
				"	max(max_smoke_val) max_smoke_val,\r\n" + 
				"	min(min_temp_val) min_temp_val,\r\n" + 
				"	avg(avg_temp_val) avg_temp_val,\r\n" + 
				"	max(max_temp_val) max_temp_val,\r\n" + 
				"	min(min_co_val) min_co_val,\r\n" + 
				"	avg(avg_co_val) avg_co_val,\r\n" + 
				"	max(max_co_val) max_co_val,\r\n" + 
				"	sum(flame_cnt) flame_cnt\r\n" + 
				"from iot_data_hist_stat\r\n" + 
				"where created_dtm >= :createdDtm\r\n" + 
				"and time_mode = '1H'\r\n" + 
				"group by concat(substr(created_dtm, 1,10),' 00:00'), interior_code,device_id",
				new MapSqlParameterSource() {{
					addValue("createdDtm",createdDtm);
				}});
	}
	/**
	 * 7. hst테이블 클랜징
	1시간전 데이터 삭제
	stat최종이 1시간전데이터이면 삭제안함
	==> IotDataHistDao에서 처리
	*/
	/**
	 * 8. stat테이블 클랜징
		8.1. 분단위 => 1일전 데이터 삭제
		  - 시간단위가 1일전이면 삭제안함
	 */
	@Override
	public int cleanseMinuteData() {
		return jdbcTemplate.update("delete from iot_data_hist_stat \r\n" + 
				"where created_dtm < date_format(date_sub(now(), interval 1 day), '%Y-%m-%d %H:%i')\r\n" + 
				//"and created_dtm < (select ifnull(max(created_dtm), date_format(date_sub(now(), interval 1 day), '%Y-%m-%d %H:%i:00.000')) from iot_data_hist_stat where time_mode = '1M');\r\n" + 
				"and time_mode='1M'");
	}
	/**
	 * 8. stat테이블 클랜징
		8.2. 시간단위=> 30일전 데이터 삭제
	 */
	@Override
	public int cleanseHourlyData() {
		return jdbcTemplate.update("delete from iot_data_hist_stat \r\n" + 
				"where created_dtm < date_format(date_sub(now(), interval 30 day), '%Y-%m-%d %H:00')\r\n" + 
				//"and created_dtm < (select ifnull(max(created_dtm), date_format(date_sub(now(), interval 30 day), '%Y-%m-%d %H:00:00.000')) from iot_data_hist_stat where time_mode = '1H');\r\n" + 
				"and time_mode in ('1H','6H')");
	}
	
	/**
	 * 최근1시간 timeMode=1H
	 * 최근1일 timeMode=1D
	 * 최근1달 timeMode=1m	 
	 */
	@Override
	public Optional<List<IotDataHistStat>> getStatsCnt(PageRequest req) {
		TimeMode timeMode = new TimeMode(req.getParamMap());
		
		SearchMapObj searchMapObj = new SearchMapObj(req.getSearchMap(), false);
		List<IotDataHistStat> t = namedParameterJdbcTemplate.query
				("select interior_code,device_id, " + 
						"  min(min_humid_val) min_humid_val,\r\n" + 
						"	avg(avg_humid_val) avg_humid_val,\r\n" + 
						"	max(max_humid_val) max_humid_val,\r\n" + 
						"	min(min_smoke_val) min_smoke_val,\r\n" + 
						"	avg(avg_smoke_val) avg_smoke_val,\r\n" + 
						"	max(max_smoke_val) max_smoke_val,\r\n" + 
						"	min(min_temp_val) min_temp_val,\r\n" + 
						"	avg(avg_temp_val) avg_temp_val,\r\n" + 
						"	max(max_temp_val) max_temp_val,\r\n" + 
						"	min(min_co_val) min_co_val,\r\n" + 
						"	avg(avg_co_val) avg_co_val,\r\n" + 
						"	max(max_co_val) max_co_val,\r\n" + 
						"	sum(flame_cnt) flame_cnt " + 
						" from iot_data_hist_stat " +
						" where created_dtm >= date_format(DATE_SUB(NOW(), INTERVAL "+timeMode.getStrTime()+"),'%Y-%m-%d "+timeMode.getStrDateFrmt()+"') " + 
						searchMapObj.andQuery() + 
						" group by interior_code,device_id " + 
						" order by interior_code,device_id"
						, new MapSqlParameterSource(req.getParamMap())
						, new BeanPropertyRowMapper<IotDataHistStat>(IotDataHistStat.class));
		return Optional.of(t);
	}

	@Override
	public Optional<List<IotData>> getRealtimeChartData(PageRequest req) {
		TimeMode timeMode = new TimeMode(req.getParamMap());
		SearchMapObj searchMapObj = new SearchMapObj(req.getSearchMap(), false);
		String fieldList;
		if (Constant.IOT_REALTIME_CHART_VAL_LEVEL_MAX.get().equals(req.getParamMap().get("valLevel"))) {
			fieldList = fieldsRealChartMax;
		} else if (Constant.IOT_REALTIME_CHART_VAL_LEVEL_MIN.get().equals(req.getParamMap().get("valLevel"))) {
			fieldList = fieldsRealChartMin;
		} else {//if (Constant.IOT_REALTIME_CHART_VAL_LEVEL_AVG.get().equals(req.getParamMap().get("valLevel"))) {
			fieldList = fieldsRealChartAvg;
		}
		List<IotData> t = namedParameterJdbcTemplate.query
				("select "+fieldList+" from iot_data_hist_stat b, " +
						"(select distinct created_dtm idx_dtm from iot_data_hist_stat " +
						" where created_dtm >= date_format(DATE_SUB(NOW(), INTERVAL "+timeMode.getStrTime()+"),'%Y-%m-%d "+timeMode.getStrDateFrmt()+"') and time_mode='"+timeMode.getTimeMode()+"' " + 
						" order by created_dtm desc limit "+req.getParamMap().get("realtimeCount")+" ) a"+ 
						"	where created_dtm = a.idx_dtm " + 
						searchMapObj.andQuery() +
						" order by interior_code,device_id,created_dtm "
						, new MapSqlParameterSource(req.getParamMap())
						, new BeanPropertyRowMapper<IotData>(IotData.class));
		return Optional.of(t);
	}
}
