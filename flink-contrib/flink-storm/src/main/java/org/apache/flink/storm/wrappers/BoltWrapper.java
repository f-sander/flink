/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.storm.wrappers;

import backtype.storm.generated.StormTopology;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.utils.Utils;
import com.google.common.collect.Sets;
import org.apache.flink.api.common.ExecutionConfig.GlobalJobParameters;
import org.apache.flink.api.java.tuple.Tuple0;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.api.java.tuple.Tuple25;
import org.apache.flink.storm.util.StormConfig;
import org.apache.flink.streaming.api.operators.AbstractStreamOperator;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.api.operators.TimestampedCollector;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;

import java.util.Collection;
import java.util.HashMap;

/**
 * A {@link BoltWrapper} wraps an {@link IRichBolt} in order to execute the Storm bolt within a Flink Streaming program.
 * It takes the Flink input tuples of type {@code IN} and transforms them into {@link StormTuple}s that the bolt can
 * process. Furthermore, it takes the bolt's output tuples and transforms them into Flink tuples of type {@code OUT}
 * (see {@link AbstractStormCollector} for supported types).<br/>
 * <br/>
 * <strong>Works for single input streams only! See {@link MergedInputsBoltWrapper} for multi-input stream
 * Bolts.</strong>
 */
public class BoltWrapper<IN, OUT> extends AbstractStreamOperator<OUT> implements OneInputStreamOperator<IN, OUT> {
	private static final long serialVersionUID = -4788589118464155835L;

	/** The default input component ID. */
	public final static String DEFAULT_ID = "default ID";
	/** The default bolt ID. */
	public final static String DEFUALT_BOLT_NAME = "Unnamed Bolt";

	/** The wrapped Storm {@link IRichBolt bolt}. */
	protected final IRichBolt bolt;
	/** The name of the bolt. */
	private final String name;
	/** Number of attributes of the bolt's output tuples per stream. */
	private final HashMap<String, Integer> numberOfAttributes;

	/** The original Storm topology. */
	private StormTopology stormTopology;
	/** The topology context of the bolt. */
	private transient TopologyContext topologyContext;

	/** The stream ID of the input stream for this bolt. */
	private final String inputStreamId;
	/** The component ID of the input stream for this bolt. */
	private final String inputComponentId;
	/** The schema (ie, ordered field names) of the input stream. */
	private final Fields inputSchema;

	/**
	 * We have to use this because Operators must output {@link StreamRecord}.
	 */
	protected transient TimestampedCollector<OUT> flinkCollector;

	/**
	 * Instantiates a new {@link BoltWrapper} that wraps the given Storm {@link IRichBolt bolt} such that it can be used
	 * within a Flink streaming program. As no input schema is defined, attribute-by-name access in only possible for
	 * POJO input types. The output type will be one of {@link Tuple0} to {@link Tuple25} depending on the bolt's
	 * declared number of attributes.
	 * 
	 * @param bolt
	 *            The Storm {@link IRichBolt bolt} to be used.
	 * @throws IllegalArgumentException
	 *             If the number of declared output attributes is not with range [0;25].
	 */
	public BoltWrapper(final IRichBolt bolt) throws IllegalArgumentException {
		this(bolt, null, (Collection<String>) null);
	}

	/**
	 * Instantiates a new {@link BoltWrapper} that wraps the given Storm {@link IRichBolt bolt} such that it can be used
	 * within a Flink streaming program. The given input schema enable attribute-by-name access for input types
	 * {@link Tuple0} to {@link Tuple25}. The output type will be one of {@link Tuple0} to {@link Tuple25} depending on
	 * the bolt's declared number of attributes.
	 * 
	 * @param bolt
	 *            The Storm {@link IRichBolt bolt} to be used.
	 * @param inputSchema
	 *            The schema (ie, ordered field names) of the input stream. @throws IllegalArgumentException
	 * @throws IllegalArgumentException
	 *             If the number of declared output attributes is not with range [0;25].
	 */
	public BoltWrapper(final IRichBolt bolt, final Fields inputSchema)
			throws IllegalArgumentException {
		this(bolt, inputSchema, (Collection<String>) null);
	}

	/**
	 * Instantiates a new {@link BoltWrapper} that wraps the given Storm {@link IRichBolt bolt} such that it can be used
	 * within a Flink streaming program. As no input schema is defined, attribute-by-name access in only possible for
	 * POJO input types. The output type can be any type if parameter {@code rawOutput} is {@code true} and the bolt's
	 * number of declared output tuples is 1. If {@code rawOutput} is {@code false} the output type will be one of
	 * {@link Tuple0} to {@link Tuple25} depending on the bolt's declared number of attributes.
	 * 
	 * @param bolt
	 *            The Storm {@link IRichBolt bolt} to be used.
	 * @param rawOutputs
	 *            Contains stream names if a single attribute output stream, should not be of type {@link Tuple1} but be
	 *            of a raw type.
	 * @throws IllegalArgumentException
	 *             If {@code rawOuput} is {@code true} and the number of declared output attributes is not 1 or if
	 *             {@code rawOuput} is {@code false} and the number of declared output attributes is not within range
	 *             [1;25].
	 */
	public BoltWrapper(final IRichBolt bolt, final String[] rawOutputs)
			throws IllegalArgumentException {
		this(bolt, null, Sets.newHashSet(rawOutputs));
	}

	/**
	 * Instantiates a new {@link BoltWrapper} that wraps the given Storm {@link IRichBolt bolt} such that it can be used
	 * within a Flink streaming program. As no input schema is defined, attribute-by-name access in only possible for
	 * POJO input types. The output type can be any type if parameter {@code rawOutput} is {@code true} and the bolt's
	 * number of declared output tuples is 1. If {@code rawOutput} is {@code false} the output type will be one of
	 * {@link Tuple0} to {@link Tuple25} depending on the bolt's declared number of attributes.
	 * 
	 * @param bolt
	 *            The Storm {@link IRichBolt bolt} to be used.
	 * @param rawOutputs
	 *            Contains stream names if a single attribute output stream, should not be of type {@link Tuple1} but be
	 *            of a raw type.
	 * @throws IllegalArgumentException
	 *             If {@code rawOuput} is {@code true} and the number of declared output attributes is not 1 or if
	 *             {@code rawOuput} is {@code false} and the number of declared output attributes is not with range
	 *             [1;25].
	 */
	public BoltWrapper(final IRichBolt bolt, final Collection<String> rawOutputs)
			throws IllegalArgumentException {
		this(bolt, null, rawOutputs);
	}

	/**
	 * Instantiates a new {@link BoltWrapper} that wraps the given Storm {@link IRichBolt bolt} such that it can be used
	 * within a Flink streaming program. The given input schema enable attribute-by-name access for input types
	 * {@link Tuple0} to {@link Tuple25}. The output type can be any type if parameter {@code rawOutput} is {@code true}
	 * and the bolt's number of declared output tuples is 1. If {@code rawOutput} is {@code false} the output type will
	 * be one of {@link Tuple0} to {@link Tuple25} depending on the bolt's declared number of attributes.
	 * 
	 * @param bolt
	 *            The Storm {@link IRichBolt bolt} to be used.
	 * @param inputSchema
	 *            The schema (ie, ordered field names) of the input stream.
	 * @param rawOutputs
	 *            Contains stream names if a single attribute output stream, should not be of type {@link Tuple1} but be
	 *            of a raw type.
	 * @throws IllegalArgumentException
	 *             If {@code rawOuput} is {@code true} and the number of declared output attributes is not 1 or if
	 *             {@code rawOuput} is {@code false} and the number of declared output attributes is not with range
	 *             [0;25].
	 */
	public BoltWrapper(final IRichBolt bolt, final Fields inputSchema, final String[] rawOutputs)
			throws IllegalArgumentException {
		this(bolt, inputSchema, Sets.newHashSet(rawOutputs));
	}

	/**
	 * Instantiates a new {@link BoltWrapper} that wraps the given Storm {@link IRichBolt bolt} such that it can be used
	 * within a Flink streaming program. The given input schema enable attribute-by-name access for input types
	 * {@link Tuple0} to {@link Tuple25}. The output type can be any type if parameter {@code rawOutput} is {@code true}
	 * and the bolt's number of declared output tuples is 1. If {@code rawOutput} is {@code false} the output type will
	 * be one of {@link Tuple0} to {@link Tuple25} depending on the bolt's declared number of attributes.
	 * 
	 * @param bolt
	 *            The Storm {@link IRichBolt bolt} to be used.
	 * @param inputSchema
	 *            The schema (ie, ordered field names) of the input stream. @throws IllegalArgumentException If
	 *            {@code rawOuput} is {@code true} and the number of declared output attributes is not 1 or if
	 *            {@code rawOuput} is {@code false} and the number of declared output attributes is not with range
	 * @param rawOutputs
	 *            Contains stream names if a single attribute output stream, should not be of type {@link Tuple1} but be
	 *            of a raw type.
	 * @throws IllegalArgumentException
	 *             If {@code rawOuput} is {@code true} and the number of declared output attributes is not 1 or if
	 *             {@code rawOuput} is {@code false} and the number of declared output attributes is not with range
	 *             [0;25].
	 */
	public BoltWrapper(final IRichBolt bolt, final Fields inputSchema,
			final Collection<String> rawOutputs) throws IllegalArgumentException {
		this(bolt, DEFUALT_BOLT_NAME, Utils.DEFAULT_STREAM_ID, DEFAULT_ID, inputSchema, rawOutputs);
	}

	/**
	 * Instantiates a new {@link BoltWrapper} that wraps the given Storm {@link IRichBolt bolt} such that it can be used
	 * within a Flink streaming program. The given input schema enable attribute-by-name access for input types
	 * {@link Tuple0} to {@link Tuple25}. The output type can be any type if parameter {@code rawOutput} is {@code true}
	 * and the bolt's number of declared output tuples is 1. If {@code rawOutput} is {@code false} the output type will
	 * be one of {@link Tuple0} to {@link Tuple25} depending on the bolt's declared number of attributes.
	 * 
	 * @param bolt
	 *            The Storm {@link IRichBolt bolt} to be used.
	 * @param name
	 *            The name of the bolt.
	 * @param inputStreamId
	 *            The stream id of the input stream for this bolt
	 * @param inputComponentId
	 *            The component id of the input stream for this bolt
	 * @param inputSchema
	 *            The schema (ie, ordered field names) of the input stream.
	 * @param rawOutputs
	 *            Contains stream names if a single attribute output stream, should not be of type {@link Tuple1} but be
	 *            of a raw type.
	 * @throws IllegalArgumentException
	 *             If {@code rawOuput} is {@code true} and the number of declared output attributes is not 1 or if
	 *             {@code rawOuput} is {@code false} and the number of declared output attributes is not with range
	 *             [0;25].
	 */
	public BoltWrapper(final IRichBolt bolt, final String name, final String inputStreamId,
			final String inputComponentId, final Fields inputSchema,
			final Collection<String> rawOutputs) throws IllegalArgumentException {
		this.bolt = bolt;
		this.name = name;
		this.inputComponentId = inputComponentId;
		this.inputStreamId = inputStreamId;
		this.inputSchema = inputSchema;
		this.numberOfAttributes = WrapperSetupHelper.getNumberOfAttributes(bolt, rawOutputs);
	}

	/**
	 * Sets the original Storm topology.
	 * 
	 * @param stormTopology
	 *            The original Storm topology.
	 */
	public void setStormTopology(StormTopology stormTopology) {
		this.stormTopology = stormTopology;
	}

	@Override
	public void open() throws Exception {
		super.open();

		this.flinkCollector = new TimestampedCollector<>(this.output);
		final OutputCollector stormCollector = new OutputCollector(new BoltCollector<OUT>(
				this.numberOfAttributes, this.flinkCollector));

		GlobalJobParameters config = getExecutionConfig().getGlobalJobParameters();
		StormConfig stormConfig = new StormConfig();

		if (config != null) {
			if (config instanceof StormConfig) {
				stormConfig = (StormConfig) config;
			} else {
				stormConfig.putAll(config.toMap());
			}
		}

		this.topologyContext = WrapperSetupHelper.createTopologyContext(
				getRuntimeContext(), this.bolt, this.name, this.stormTopology, stormConfig);
		this.bolt.prepare(stormConfig, this.topologyContext, stormCollector);
	}

	@Override
	public void dispose() {
		this.bolt.cleanup();
	}

	@Override
	public void processElement(final StreamRecord<IN> element) throws Exception {
		this.flinkCollector.setTimestamp(element.getTimestamp());
		IN value = element.getValue();
		this.bolt.execute(new StormTuple<>(value, this.inputSchema, this.topologyContext
				.getThisTaskId(), this.inputStreamId, this.inputComponentId));
	}

	@Override
	public void processWatermark(Watermark mark) throws Exception {
		this.output.emitWatermark(mark);
	}

}
