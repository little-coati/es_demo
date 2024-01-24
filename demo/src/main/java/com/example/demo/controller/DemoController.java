package com.example.demo.controller;

import com.example.demo.DataPointDTO;
import com.example.demo.DataPointDataExtremumVO;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.metrics.ParsedAvg;
import org.elasticsearch.search.aggregations.metrics.ParsedMax;
import org.elasticsearch.search.aggregations.metrics.ParsedMin;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Karl
 * @since 2022-08-15
 */
@RestController
@RequestMapping("/es")
public class DemoController {

    // 这里以一个用水量数据点举例

    private final String INDEX = "datapoint";
    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @GetMapping("/list")
    public List<DataPointDTO> getByPointId() {
        long start = 1661270400000L; // 2022-08-24 00:00:00
        long end = 1661356800000L; // 2022-08-25 00:00:00
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery().must(QueryBuilders.termQuery("pointId", 1550413369710178306L))
                        .filter(QueryBuilders.rangeQuery("receivedAt").from(start).to(end)))
                .withSort(SortBuilders.fieldSort("receivedAt").order(SortOrder.ASC));
        NativeSearchQuery build = builder.build();
        // 设置最大返回结果数量，和分页冲突
        build.setMaxResults(20000000);
        SearchHits<DataPointDTO> hits = elasticsearchRestTemplate.search(build, DataPointDTO.class, IndexCoordinates.of(INDEX));
        return hits.getSearchHits().stream().map(SearchHit::getContent).collect(Collectors.toList());
    }

    @GetMapping("/page")
    public Page<DataPointDTO> getPage() {
        PageRequest request = PageRequest.of(1, 10);
        long start = 1661270400000L; // 2022-08-24 00:00:00
        long end = 1661356800000L; // 2022-08-25 00:00:00
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery().must(QueryBuilders.termQuery("pointId", 1550413369710178306L))
                        .filter(QueryBuilders.rangeQuery("receivedAt").from(start).to(end)))
                .withSort(SortBuilders.fieldSort("receivedAt").order(SortOrder.ASC));
        builder.withPageable(request);
        NativeSearchQuery build = builder.build();
        // 返回所有命中数量
        build.setTrackTotalHits(true);
        SearchHits<DataPointDTO> hits = elasticsearchRestTemplate.search(build, DataPointDTO.class, IndexCoordinates.of(INDEX));
        List<DataPointDTO> content = hits.getSearchHits().stream().map(SearchHit::getContent).collect(Collectors.toList());
        return new PageImpl<>(content, request, hits.getTotalHits());
    }

    @GetMapping("/extremum")
    public List<DataPointDataExtremumVO> getExtremum() {
        long start = 1660924800000L; // 2022-08-20 00:00:00
        long end = 1661356800000L; // 2022-08-25 00:00:00
        // 求取每天的极值
        DateHistogramAggregationBuilder dateBuilder = AggregationBuilders.dateHistogram("date").field("receivedAt")
                .fixedInterval(DateHistogramInterval.DAY).timeZone(ZoneOffset.ofHours(8))
                .subAggregation(AggregationBuilders.max("max").field("value"))
                .subAggregation(AggregationBuilders.min("min").field("value"))
                .subAggregation(AggregationBuilders.avg("avg").field("value"));
        // 正常时间范围筛选
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery().must(QueryBuilders.termQuery("pointId", 1550413369710178306L))
                        .filter(QueryBuilders.rangeQuery("receivedAt").from(start).to(end, false)));
        builder.addAggregation(dateBuilder);
        NativeSearchQuery build = builder.build();
        SearchHits<DataPointDTO> hits = elasticsearchRestTemplate.search(build, DataPointDTO.class, IndexCoordinates.of(INDEX));
        List<DataPointDTO> content = hits.getSearchHits().stream().map(SearchHit::getContent).collect(Collectors.toList());
        ParsedDateHistogram date = hits.getAggregations().get("date");
        List<? extends Histogram.Bucket> buckets = date.getBuckets();
        List<DataPointDataExtremumVO> res = new ArrayList<>(buckets.size());
        Long pointId = hits.getSearchHits().get(0).getContent().getPointId();
        for (Histogram.Bucket bucket : buckets) {
            Aggregations aggregations = bucket.getAggregations();
            ParsedMin min = aggregations.get("min");
            ParsedMax max = aggregations.get("max");
            ParsedAvg avg = aggregations.get("avg");
            DataPointDataExtremumVO vo = new DataPointDataExtremumVO();
            long a = Long.parseLong(bucket.getKeyAsString());
            vo.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(a), ZoneOffset.ofHours(8)).toLocalDate());
            double maxValue = max.getValue();
            double minValue = min.getValue();
            vo.setMax(maxValue);
            vo.setMin(minValue);
            vo.setAvg(avg.getValue());
            long b = vo.getDate().plusDays(1L).atStartOfDay(ZoneOffset.ofHours(8)).toInstant().toEpochMilli();
            NativeSearchQuery builderIn = new NativeSearchQueryBuilder().withQuery(QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("pointId", pointId))
                            .filter(QueryBuilders.rangeQuery("receivedAt").gte(a).lt(b)).must(QueryBuilders.termsQuery("value", new double[]{minValue, maxValue})))
                    .withSort(SortBuilders.fieldSort("value").order(SortOrder.ASC))
                    .build();
            SearchHits<DataPointDTO> dataHits = elasticsearchRestTemplate.search(builderIn, DataPointDTO.class, IndexCoordinates.of(INDEX));
            Long minTime = dataHits.getSearchHits().get(0).getContent().getReceivedAt();
            Long maxTime = dataHits.getSearchHits().get(1).getContent().getReceivedAt();
            vo.setMinTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(minTime), ZoneOffset.ofHours(8)));
            vo.setMaxTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(maxTime), ZoneOffset.ofHours(8)));
            res.add(vo);
        }
        return res;
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("/redis")
    public void redis(String key,Integer value) {
        // redisTemplate.opsForValue().set(key, value + "");
        stringRedisTemplate.opsForValue().set(key, value + "");
    }

    @GetMapping("/rediss")
    public Object rediss(String key) {
        String o = (String) redisTemplate.opsForValue().get(key);
        return o;
    }


    public static void main(String[] args) {
        int dayOfMonth = LocalDate.of(2022, 10, 1).atStartOfDay().minusDays(1).getDayOfMonth();
        long savePlus = dayOfMonth * (24 * 60 * 60 * 1000L);
        long dataPlus = 24 * 60 * 60 * 1000L;
        long end = LocalDate.of(2022, 10, 1).atStartOfDay().toInstant(ZoneOffset.ofHours(8)).toEpochMilli() - dataPlus;
        long start = end - savePlus;
        System.out.println(start);
    }
}