/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.ingest;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.cluster.Diff;
import org.elasticsearch.cluster.DiffableUtils;
import org.elasticsearch.cluster.NamedDiff;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.util.Maps;
import org.elasticsearch.common.xcontent.ChunkedToXContentHelper;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Holds the ingest pipelines that are available in the cluster
 */
public final class IngestMetadata implements Metadata.ProjectCustom {

    public static final String TYPE = "ingest";
    private static final ParseField PIPELINES_FIELD = new ParseField("pipeline");
    private static final ObjectParser<List<PipelineConfiguration>, Void> INGEST_METADATA_PARSER = new ObjectParser<>(
        "ingest_metadata",
        ArrayList::new
    );

    static {
        INGEST_METADATA_PARSER.declareObjectArray(List::addAll, PipelineConfiguration.getParser(), PIPELINES_FIELD);
    }

    // We can't use Pipeline class directly in cluster state, because we don't have the processor factories around when
    // IngestMetadata is registered as custom metadata.
    private final Map<String, PipelineConfiguration> pipelines;

    public IngestMetadata(Map<String, PipelineConfiguration> pipelines) {
        this.pipelines = Map.copyOf(pipelines);
    }

    @Override
    public String getWriteableName() {
        return TYPE;
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersions.MINIMUM_COMPATIBLE;
    }

    public Map<String, PipelineConfiguration> getPipelines() {
        return pipelines;
    }

    public IngestMetadata(StreamInput in) throws IOException {
        int size = in.readVInt();
        Map<String, PipelineConfiguration> pipelines = Maps.newMapWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            PipelineConfiguration pipeline = PipelineConfiguration.readFrom(in);
            pipelines.put(pipeline.getId(), pipeline);
        }
        this.pipelines = Map.copyOf(pipelines);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(pipelines.size());
        for (PipelineConfiguration pipeline : pipelines.values()) {
            pipeline.writeTo(out);
        }
    }

    public static IngestMetadata fromXContent(XContentParser parser) throws IOException {
        Map<String, PipelineConfiguration> pipelines = new HashMap<>();
        List<PipelineConfiguration> configs = INGEST_METADATA_PARSER.parse(parser, null);
        for (PipelineConfiguration pipeline : configs) {
            pipelines.put(pipeline.getId(), pipeline);
        }
        return new IngestMetadata(pipelines);
    }

    @Override
    public Iterator<? extends ToXContent> toXContentChunked(ToXContent.Params ignored) {
        return ChunkedToXContentHelper.array(PIPELINES_FIELD.getPreferredName(), pipelines.values().iterator());
    }

    @Override
    public EnumSet<Metadata.XContentContext> context() {
        return Metadata.ALL_CONTEXTS;
    }

    @Override
    public Diff<Metadata.ProjectCustom> diff(Metadata.ProjectCustom before) {
        return new IngestMetadataDiff((IngestMetadata) before, this);
    }

    public static NamedDiff<Metadata.ProjectCustom> readDiffFrom(StreamInput in) throws IOException {
        return new IngestMetadataDiff(in);
    }

    static class IngestMetadataDiff implements NamedDiff<Metadata.ProjectCustom> {

        final Diff<Map<String, PipelineConfiguration>> pipelines;

        IngestMetadataDiff(IngestMetadata before, IngestMetadata after) {
            this.pipelines = DiffableUtils.diff(before.pipelines, after.pipelines, DiffableUtils.getStringKeySerializer());
        }

        IngestMetadataDiff(StreamInput in) throws IOException {
            pipelines = DiffableUtils.readJdkMapDiff(
                in,
                DiffableUtils.getStringKeySerializer(),
                PipelineConfiguration::readFrom,
                PipelineConfiguration::readDiffFrom
            );
        }

        @Override
        public Metadata.ProjectCustom apply(Metadata.ProjectCustom part) {
            return new IngestMetadata(pipelines.apply(((IngestMetadata) part).pipelines));
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            pipelines.writeTo(out);
        }

        @Override
        public String getWriteableName() {
            return TYPE;
        }

        @Override
        public TransportVersion getMinimalSupportedVersion() {
            return TransportVersions.MINIMUM_COMPATIBLE;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IngestMetadata that = (IngestMetadata) o;

        return pipelines.equals(that.pipelines);

    }

    @Override
    public int hashCode() {
        return pipelines.hashCode();
    }

    /**
     * Returns a copy of this object with processor upgrades applied, if necessary. Otherwise, returns this object.
     *
     * <p>The given upgrader is applied to the config map for any processor of the given type.
     */
    public IngestMetadata maybeUpgradeProcessors(String processorType, ProcessorConfigUpgrader processorConfigUpgrader) {
        Map<String, PipelineConfiguration> newPipelines = null; // as an optimization, we will lazily copy the map only if needed
        for (Map.Entry<String, PipelineConfiguration> entry : pipelines.entrySet()) {
            String pipelineId = entry.getKey();
            PipelineConfiguration originalPipeline = entry.getValue();
            PipelineConfiguration upgradedPipeline = originalPipeline.maybeUpgradeProcessors(processorType, processorConfigUpgrader);
            if (upgradedPipeline.equals(originalPipeline) == false) {
                if (newPipelines == null) {
                    newPipelines = new HashMap<>(pipelines);
                }
                newPipelines.put(pipelineId, upgradedPipeline);
            }
        }
        return newPipelines != null ? new IngestMetadata(newPipelines) : this;
    }

    /**
     * Functional interface for upgrading processor configs. An implementation of this will be associated with a specific processor type.
     */
    public interface ProcessorConfigUpgrader {

        /**
         * Upgrades the config for an individual processor of the appropriate type, if necessary.
         *
         * @param processorConfig The config to upgrade, which will be mutated if required
         * @return Whether an upgrade was required
         */
        boolean maybeUpgrade(Map<String, Object> processorConfig);
    }
}
