/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.scala

import org.apache.flink.api.common.functions.{FilterFunction, FlatMapFunction, MapFunction, Partitioner}
import org.apache.flink.api.common.io.OutputFormat
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.java.tuple.{Tuple => JavaTuple}
import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import org.apache.flink.api.scala.operators.ScalaCsvOutputFormat
import org.apache.flink.core.fs.{FileSystem, Path}
import org.apache.flink.streaming.api.collector.selector.OutputSelector
import org.apache.flink.streaming.api.datastream.{AllWindowedStream => JavaAllWindowedStream, DataStream => JavaStream, KeyedStream => JavaKeyedStream, _}
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.{AscendingTimestampExtractor, TimestampExtractor}
import org.apache.flink.streaming.api.windowing.assigners._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.{GlobalWindow, TimeWindow, Window}
import org.apache.flink.streaming.util.serialization.SerializationSchema
import org.apache.flink.util.Collector

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

class DataStream[T](stream: JavaStream[T]) {

  /**
   * Gets the underlying java DataStream object.
   */
  private[flink] def javaStream: JavaStream[T] = stream

  /**
    * Returns the [[StreamExecutionEnvironment]] associated with the current [[DataStream]].
    * @return associated execution environment
    */
  def getExecutionEnvironment: StreamExecutionEnvironment =
    new StreamExecutionEnvironment(stream.getExecutionEnvironment)

  /**
   * Returns the ID of the DataStream.
   *
   * @return ID of the DataStream
   */
  def getId = stream.getId

  /**
   * Returns the TypeInformation for the elements of this DataStream.
   */
  def getType(): TypeInformation[T] = stream.getType()

  /**
   * Sets the parallelism of this operation. This must be at least 1.
   */
  def setParallelism(parallelism: Int): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[_, _] => ds.setParallelism(parallelism)
      case _ =>
        throw new UnsupportedOperationException("Operator " + stream.toString +  " cannot " +
          "have " +
          "parallelism.")
    }
    this
  }

  /**
   * Returns the parallelism of this operation.
   */
  def getParallelism = stream.getParallelism

  /**
   * Returns the execution config.
   */
  def getExecutionConfig = stream.getExecutionConfig

  /**
   * Gets the name of the current data stream. This name is
   * used by the visualization and logging during runtime.
   *
   * @return Name of the stream.
   */
  def getName : String = stream match {
    case stream : SingleOutputStreamOperator[T,_] => stream.getName
    case _ => throw new
        UnsupportedOperationException("Only supported for operators.")
  }

  /**
   * Sets the name of the current data stream. This name is
   * used by the visualization and logging during runtime.
   *
   * @return The named operator
   */
  def name(name: String) : DataStream[T] = stream match {
    case stream : SingleOutputStreamOperator[T,_] => stream.name(name)
    case _ => throw new UnsupportedOperationException("Only supported for operators.")
    this
  }

  /**
    * Sets an ID for this operator.
    *
    * The specified ID is used to assign the same operator ID across job
    * submissions (for example when starting a job from a savepoint).
    *
    * <strong>Important</strong>: this ID needs to be unique per
    * transformation and job. Otherwise, job submission will fail.
    *
    * @param uid The unique user-specified ID of this transformation.
    * @return The operator with the specified ID.
    */
  def uid(uid: String) : DataStream[T] = javaStream match {
    case stream : SingleOutputStreamOperator[T,_] => stream.uid(uid)
    case _ => throw new UnsupportedOperationException("Only supported for operators.")
    this
  }
  
  /**
   * Turns off chaining for this operator so thread co-location will not be
   * used as an optimization. </p> Chaining can be turned off for the whole
   * job by [[StreamExecutionEnvironment.disableOperatorChaining()]]
   * however it is not advised for performance considerations.
   *
   */
  def disableChaining(): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[_, _] => ds.disableChaining();
      case _ =>
        throw new UnsupportedOperationException("Only supported for operators.")
    }
    this
  }

  /**
   * Starts a new task chain beginning at this operator. This operator will
   * not be chained (thread co-located for increased performance) to any
   * previous tasks even if possible.
   *
   */
  def startNewChain(): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[_, _] => ds.startNewChain();
      case _ =>
        throw new UnsupportedOperationException("Only supported for operators.")
    }
    this
  }

  /**
   * Isolates the operator in its own resource group. This will cause the
   * operator to grab as many task slots as its degree of parallelism. If
   * there are no free resources available, the job will fail to start.
   * All subsequent operators are assigned to the default resource group.
   *
   */
  def isolateResources(): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[_, _] => ds.isolateResources();
      case _ =>
        throw new UnsupportedOperationException("Only supported for operators.")
    }
    this
  }

  /**
   * By default all operators in a streaming job share the same resource
   * group. Each resource group takes as many task manager slots as the
   * maximum parallelism operator in that group. By calling this method, this
   * operators starts a new resource group and all subsequent operators will
   * be added to this group unless specified otherwise. Please note that
   * local executions have by default as many available task slots as the
   * environment parallelism, so in order to start a new resource group the
   * degree of parallelism for the operators must be decreased from the
   * default.
   */
  def startNewResourceGroup(): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[_, _] => ds.startNewResourceGroup();
      case _ =>
        throw new UnsupportedOperationException("Only supported for operators.")
    }
    this
  }

  /**
   * Sets the maximum time frequency (ms) for the flushing of the output
   * buffer. By default the output buffers flush only when they are full.
   *
   * @param timeoutMillis
   * The maximum time between two output flushes.
   * @return The operator with buffer timeout set.
   */
  def setBufferTimeout(timeoutMillis: Long): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[_, _] => ds.setBufferTimeout(timeoutMillis);
      case _ =>
        throw new UnsupportedOperationException("Only supported for operators.")
    }
    this
  }

  /**
   * Creates a new DataStream by merging DataStream outputs of
   * the same type with each other. The DataStreams merged using this operator
   * will be transformed simultaneously.
   *
   */
  def union(dataStreams: DataStream[T]*): DataStream[T] =
    stream.union(dataStreams.map(_.javaStream): _*)

  /**
   * Creates a new ConnectedStreams by connecting
   * DataStream outputs of different type with each other. The
   * DataStreams connected using this operators can be used with CoFunctions.
   */
  def connect[T2](dataStream: DataStream[T2]): ConnectedStreams[T, T2] =
    stream.connect(dataStream.javaStream)

  /**
   * Groups the elements of a DataStream by the given key positions (for tuple/array types) to
   * be used with grouped operators like grouped reduce or grouped aggregations.
   */
  def keyBy(fields: Int*): KeyedStream[T, JavaTuple] = stream.keyBy(fields: _*)

  /**
   * Groups the elements of a DataStream by the given field expressions to
   * be used with grouped operators like grouped reduce or grouped aggregations.
   */
  def keyBy(firstField: String, otherFields: String*): KeyedStream[T, JavaTuple] =
   stream.keyBy(firstField +: otherFields.toArray: _*)

  /**
   * Groups the elements of a DataStream by the given K key to
   * be used with grouped operators like grouped reduce or grouped aggregations.
   */
  def keyBy[K: TypeInformation](fun: T => K): KeyedStream[T, K] = {

    val cleanFun = clean(fun)
    val keyType: TypeInformation[K] = implicitly[TypeInformation[K]]

    val keyExtractor = new KeySelector[T, K] with ResultTypeQueryable[K] {
      def getKey(in: T) = cleanFun(in)
      override def getProducedType: TypeInformation[K] = keyType
    }
    new JavaKeyedStream(stream, keyExtractor, keyType)
  }

  /**
   * Partitions the elements of a DataStream by the given key positions (for tuple/array types) to
   * be used with grouped operators like grouped reduce or grouped aggregations.
   */
  def partitionByHash(fields: Int*): DataStream[T] = stream.partitionByHash(fields: _*)

  /**
   * Groups the elements of a DataStream by the given field expressions to
   * be used with grouped operators like grouped reduce or grouped aggregations.
   */
  def partitionByHash(firstField: String, otherFields: String*): DataStream[T] =
    stream.partitionByHash(firstField +: otherFields.toArray: _*)

  /**
   * Groups the elements of a DataStream by the given K key to
   * be used with grouped operators like grouped reduce or grouped aggregations.
   */
  def partitionByHash[K: TypeInformation](fun: T => K): DataStream[T] = {

    val cleanFun = clean(fun)
    val keyExtractor = new KeySelector[T, K] with ResultTypeQueryable[K] {
      def getKey(in: T) = cleanFun(in)
      override def getProducedType: TypeInformation[K] = implicitly[TypeInformation[K]]
    }
    stream.partitionByHash(keyExtractor)
  }

  /**
   * Partitions a tuple DataStream on the specified key fields using a custom partitioner.
   * This method takes the key position to partition on, and a partitioner that accepts the key
   * type.
   * <p>
   * Note: This method works only on single field keys.
   */
  def partitionCustom[K: TypeInformation](partitioner: Partitioner[K], field: Int) : DataStream[T] =
    stream.partitionCustom(partitioner, field)

  /**
   * Partitions a POJO DataStream on the specified key fields using a custom partitioner.
   * This method takes the key expression to partition on, and a partitioner that accepts the key
   * type.
   * <p>
   * Note: This method works only on single field keys.
   */
  def partitionCustom[K: TypeInformation](partitioner: Partitioner[K], field: String)
  : DataStream[T] = stream.partitionCustom(partitioner, field)

  /**
   * Partitions a DataStream on the key returned by the selector, using a custom partitioner.
   * This method takes the key selector to get the key to partition on, and a partitioner that
   * accepts the key type.
   * <p>
   * Note: This method works only on single field keys, i.e. the selector cannot return tuples
   * of fields.
   */
  def partitionCustom[K: TypeInformation](partitioner: Partitioner[K], fun: T => K)
  : DataStream[T] = {
    val cleanFun = clean(fun)
    val keyExtractor = new KeySelector[T, K] with ResultTypeQueryable[K] {
      def getKey(in: T) = cleanFun(in)
      override def getProducedType: TypeInformation[K] = implicitly[TypeInformation[K]]
    }
    stream.partitionCustom(partitioner, keyExtractor)
  }

  /**
   * Sets the partitioning of the DataStream so that the output tuples
   * are broad casted to every parallel instance of the next component. This
   * setting only effects the how the outputs will be distributed between the
   * parallel instances of the next processing operator.
   *
   */
  def broadcast: DataStream[T] = stream.broadcast()

  /**
   * Sets the partitioning of the DataStream so that the output values all go to
   * the first instance of the next processing operator. Use this setting with care
   * since it might cause a serious performance bottleneck in the application.
   */
  def global: DataStream[T] = stream.global()

  /**
   * Sets the partitioning of the DataStream so that the output tuples
   * are shuffled to the next component. This setting only effects the how the
   * outputs will be distributed between the parallel instances of the next
   * processing operator.
   *
   */
  def shuffle: DataStream[T] = stream.shuffle()

  /**
   * Sets the partitioning of the DataStream so that the output tuples
   * are forwarded to the local subtask of the next component (whenever
   * possible). This is the default partitioner setting. This setting only
   * effects the how the outputs will be distributed between the parallel
   * instances of the next processing operator.
   *
   */
  def forward: DataStream[T] = stream.forward()

  /**
   * Sets the partitioning of the DataStream so that the output tuples
   * are distributed evenly to the next component.This setting only effects
   * the how the outputs will be distributed between the parallel instances of
   * the next processing operator.
   *
   */
  def rebalance: DataStream[T] = stream.rebalance()

  /**
   * Initiates an iterative part of the program that creates a loop by feeding
   * back data streams. To create a streaming iteration the user needs to define
   * a transformation that creates two DataStreams. The first one is the output
   * that will be fed back to the start of the iteration and the second is the output
   * stream of the iterative part.
   * <p>
   * stepfunction: initialStream => (feedback, output)
   * <p>
   * A common pattern is to use output splitting to create feedback and output DataStream.
   * Please refer to the .split(...) method of the DataStream
   * <p>
   * By default a DataStream with iteration will never terminate, but the user
   * can use the maxWaitTime parameter to set a max waiting time for the iteration head.
   * If no data received in the set time the stream terminates.
   * <p>
   * By default the feedback partitioning is set to match the input, to override this set
   * the keepPartitioning flag to true
   *
   */
  def iterate[R](stepFunction: DataStream[T] => (DataStream[T], DataStream[R]),
                    maxWaitTimeMillis:Long = 0,
                    keepPartitioning: Boolean = false) : DataStream[R] = {
    val iterativeStream = stream.iterate(maxWaitTimeMillis)

    val (feedback, output) = stepFunction(new DataStream[T](iterativeStream))
    iterativeStream.closeWith(feedback.javaStream)
    output
  }

  /**
   * Initiates an iterative part of the program that creates a loop by feeding
   * back data streams. To create a streaming iteration the user needs to define
   * a transformation that creates two DataStreams. The first one is the output
   * that will be fed back to the start of the iteration and the second is the output
   * stream of the iterative part.
   *
   * The input stream of the iterate operator and the feedback stream will be treated
   * as a ConnectedStreams where the the input is connected with the feedback stream.
   *
   * This allows the user to distinguish standard input from feedback inputs.
   *
   * <p>
   * stepfunction: initialStream => (feedback, output)
   * <p>
   * The user must set the max waiting time for the iteration head.
   * If no data received in the set time the stream terminates. If this parameter is set
   * to 0 then the iteration sources will indefinitely, so the job must be killed to stop.
   *
   */
  def iterate[R, F: TypeInformation: ClassTag](stepFunction: ConnectedStreams[T, F] =>
    (DataStream[F], DataStream[R]), maxWaitTimeMillis:Long): DataStream[R] = {
    val feedbackType: TypeInformation[F] = implicitly[TypeInformation[F]]
    val connectedIterativeStream = stream.iterate(maxWaitTimeMillis).
                                   withFeedbackType(feedbackType)

    val (feedback, output) = stepFunction(connectedIterativeStream)
    connectedIterativeStream.closeWith(feedback.javaStream)
    output
  }

  /**
   * Creates a new DataStream by applying the given function to every element of this DataStream.
   */
  def map[R: TypeInformation: ClassTag](fun: T => R): DataStream[R] = {
    if (fun == null) {
      throw new NullPointerException("Map function must not be null.")
    }
    val cleanFun = clean(fun)
    val mapper = new MapFunction[T, R] {
      def map(in: T): R = cleanFun(in)
    }

    map(mapper)
  }

  /**
   * Creates a new DataStream by applying the given function to every element of this DataStream.
   */
  def map[R: TypeInformation: ClassTag](mapper: MapFunction[T, R]): DataStream[R] = {
    if (mapper == null) {
      throw new NullPointerException("Map function must not be null.")
    }

    val outType : TypeInformation[R] = implicitly[TypeInformation[R]]
    stream.map(mapper).returns(outType).asInstanceOf[JavaStream[R]]
  }

  /**
   * Creates a new DataStream by applying the given function to every element and flattening
   * the results.
   */
  def flatMap[R: TypeInformation: ClassTag](flatMapper: FlatMapFunction[T, R]): DataStream[R] = {
    if (flatMapper == null) {
      throw new NullPointerException("FlatMap function must not be null.")
    }

    val outType : TypeInformation[R] = implicitly[TypeInformation[R]]
    stream.flatMap(flatMapper).returns(outType).asInstanceOf[JavaStream[R]]
  }

  /**
   * Creates a new DataStream by applying the given function to every element and flattening
   * the results.
   */
  def flatMap[R: TypeInformation: ClassTag](fun: (T, Collector[R]) => Unit): DataStream[R] = {
    if (fun == null) {
      throw new NullPointerException("FlatMap function must not be null.")
    }
    val cleanFun = clean(fun)
    val flatMapper = new FlatMapFunction[T, R] {
      def flatMap(in: T, out: Collector[R]) { cleanFun(in, out) }
    }
    flatMap(flatMapper)
  }

  /**
   * Creates a new DataStream by applying the given function to every element and flattening
   * the results.
   */
  def flatMap[R: TypeInformation: ClassTag](fun: T => TraversableOnce[R]): DataStream[R] = {
    if (fun == null) {
      throw new NullPointerException("FlatMap function must not be null.")
    }
    val cleanFun = clean(fun)
    val flatMapper = new FlatMapFunction[T, R] {
      def flatMap(in: T, out: Collector[R]) { cleanFun(in) foreach out.collect }
    }
    flatMap(flatMapper)
  }

  /**
   * Creates a new DataStream that contains only the elements satisfying the given filter predicate.
   */
  def filter(filter: FilterFunction[T]): DataStream[T] = {
    if (filter == null) {
      throw new NullPointerException("Filter function must not be null.")
    }
    stream.filter(filter)
  }

  /**
   * Creates a new DataStream that contains only the elements satisfying the given filter predicate.
   */
  def filter(fun: T => Boolean): DataStream[T] = {
    if (fun == null) {
      throw new NullPointerException("Filter function must not be null.")
    }
    val cleanFun = clean(fun)
    val filter = new FilterFunction[T] {
      def filter(in: T) = cleanFun(in)
    }
    this.filter(filter)
  }

  /**
   * Windows this DataStream into tumbling time windows.
   *
   * This is a shortcut for either `.window(TumblingTimeWindows.of(size))` or
   * `.window(TumblingProcessingTimeWindows.of(size))` depending on the time characteristic
   * set using
   * [[StreamExecutionEnvironment.setStreamTimeCharacteristic]].
   *
   * Note: This operation can be inherently non-parallel since all elements have to pass through
   * the same operator instance. (Only for special cases, such as aligned time windows is
   * it possible to perform this operation in parallel).
   *
   * @param size The size of the window.
   */
  def timeWindowAll(size: Time): AllWindowedStream[T, TimeWindow] = {
    val assigner = TumblingTimeWindows.of(size).asInstanceOf[WindowAssigner[T, TimeWindow]]
    windowAll(assigner)
  }

  /**
   * Windows this DataStream into sliding time windows.
   *
   * This is a shortcut for either `.window(SlidingTimeWindows.of(size, slide))` or
   * `.window(SlidingProcessingTimeWindows.of(size, slide))` depending on the time characteristic
   * set using
   * [[StreamExecutionEnvironment.setStreamTimeCharacteristic]].
   *
   * Note: This operation can be inherently non-parallel since all elements have to pass through
   * the same operator instance. (Only for special cases, such as aligned time windows is
   * it possible to perform this operation in parallel).
   *
   * @param size The size of the window.
   */
  def timeWindowAll(size: Time, slide: Time): AllWindowedStream[T, TimeWindow] = {
    val assigner = SlidingTimeWindows.of(size, slide).asInstanceOf[WindowAssigner[T, TimeWindow]]
    windowAll(assigner)
  }

  /**
   * Windows this [[DataStream]] into sliding count windows.
   *
   * Note: This operation can be inherently non-parallel since all elements have to pass through
   * the same operator instance. (Only for special cases, such as aligned time windows is
   * it possible to perform this operation in parallel).
   *
   * @param size The size of the windows in number of elements.
   * @param slide The slide interval in number of elements.
   */
  def countWindowAll(size: Long, slide: Long): AllWindowedStream[T, GlobalWindow] = {
    new AllWindowedStream(stream.countWindowAll(size, slide))
  }

  /**
   * Windows this [[DataStream]] into tumbling count windows.
   *
   * Note: This operation can be inherently non-parallel since all elements have to pass through
   * the same operator instance. (Only for special cases, such as aligned time windows is
   * it possible to perform this operation in parallel).
   *
   * @param size The size of the windows in number of elements.
   */
  def countWindowAll(size: Long): AllWindowedStream[T, GlobalWindow] = {
    new AllWindowedStream(stream.countWindowAll(size))
  }

  /**
   * Windows this data stream to a [[AllWindowedStream]], which evaluates windows
   * over a key grouped stream. Elements are put into windows by a [[WindowAssigner]]. The grouping
   * of elements is done both by key and by window.
   *
   * A [[org.apache.flink.streaming.api.windowing.triggers.Trigger]] can be defined to specify
   * when windows are evaluated. However, `WindowAssigner` have a default `Trigger`
   * that is used if a `Trigger` is not specified.
   *
   * Note: This operation can be inherently non-parallel since all elements have to pass through
   * the same operator instance. (Only for special cases, such as aligned time windows is
   * it possible to perform this operation in parallel).
   *
   * @param assigner The `WindowAssigner` that assigns elements to windows.
   * @return The trigger windows data stream.
   */
  def windowAll[W <: Window](assigner: WindowAssigner[_ >: T, W]): AllWindowedStream[T, W] = {
    new AllWindowedStream[T, W](new JavaAllWindowedStream[T, W](stream, assigner))
  }
  /**
   * Extracts a timestamp from an element and assigns it as the internal timestamp of that element.
   * The internal timestamps are, for example, used to to event-time window operations.
   *
   * If you know that the timestamps are strictly increasing you can use an
   * [[org.apache.flink.streaming.api.functions.AscendingTimestampExtractor]]. Otherwise,
   * you should provide a [[TimestampExtractor]] that also implements
   * [[TimestampExtractor#getCurrentWatermark]] to keep track of watermarks.
   *
   * @see org.apache.flink.streaming.api.watermark.Watermark
   */
  def assignTimestamps(extractor: TimestampExtractor[T]): DataStream[T] = {
    stream.assignTimestamps(clean(extractor))
  }

  /**
   * Extracts a timestamp from an element and assigns it as the internal timestamp of that element.
   * The internal timestamps are, for example, used to to event-time window operations.
   *
   * If you know that the timestamps are strictly increasing you can use an
   * [[org.apache.flink.streaming.api.functions.AscendingTimestampExtractor]]. Otherwise,
   * you should provide a [[TimestampExtractor]] that also implements
   * [[TimestampExtractor#getCurrentWatermark]] to keep track of watermarks.
   *
   * @see org.apache.flink.streaming.api.watermark.Watermark
   */
  def assignAscendingTimestamps(extractor: T => Long): DataStream[T] = {
    val cleanExtractor = clean(extractor)
    val extractorFunction = new AscendingTimestampExtractor[T] {
      def extractAscendingTimestamp(element: T, currentTimestamp: Long): Long = {
        cleanExtractor(element)
      }
    }
    stream.assignTimestamps(extractorFunction)
  }

  /**
   *
   * Operator used for directing tuples to specific named outputs using an
   * OutputSelector. Calling this method on an operator creates a new
   * [[SplitStream]].
   */
  def split(selector: OutputSelector[T]): SplitStream[T] = stream.split(selector)

  /**
   * Creates a new [[SplitStream]] that contains only the elements satisfying the
   *  given output selector predicate.
   */
  def split(fun: T => TraversableOnce[String]): SplitStream[T] = {
    if (fun == null) {
      throw new NullPointerException("OutputSelector must not be null.")
    }
    val cleanFun = clean(fun)
    val selector = new OutputSelector[T] {
      def select(in: T): java.lang.Iterable[String] = {
        cleanFun(in).toIterable.asJava
      }
    }
    split(selector)
  }

  /**
   * Creates a co-group operation. See [[CoGroupedStreams]] for an example of how the keys
   * and window can be specified.
   */
  def coGroup[T2](otherStream: DataStream[T2]): CoGroupedStreams.Unspecified[T, T2] = {
    CoGroupedStreams.createCoGroup(this, otherStream)
  }

  /**
   * Creates a join operation. See [[JoinedStreams]] for an example of how the keys
   * and window can be specified.
   */
  def join[T2](otherStream: DataStream[T2]): JoinedStreams.Unspecified[T, T2] = {
    JoinedStreams.createJoin(this, otherStream)
  }

  /**
   * Writes a DataStream to the standard output stream (stdout). For each
   * element of the DataStream the result of .toString is
   * written.
   *
   */
  def print(): DataStreamSink[T] = stream.print()

  /**
   * Writes a DataStream to the standard output stream (stderr).
   *
   * For each element of the DataStream the result of
   * [[AnyRef.toString()]] is written.
   *
   * @return The closed DataStream.
   */
  def printToErr() = stream.printToErr()

  /**
    * Writes a DataStream to the file specified by path in text format. For
    * every element of the DataStream the result of .toString is written.
    *
    * @param path The path pointing to the location the text file is written to
    * @return The closed DataStream
    */
  def writeAsText(path: String): DataStreamSink[T] =
    stream.writeAsText(path, 0L)

  /**
   * Writes a DataStream to the file specified by path in text format. The
   * writing is performed periodically, every millis milliseconds. For
   * every element of the DataStream the result of .toString
   * is written.
   *
   * @param path The path pointing to the location the text file is written to
   * @param millis The file update frequency
   * @return The closed DataStream
   */
  def writeAsText(path: String, millis: Long): DataStreamSink[T] =
    stream.writeAsText(path, millis)

  /**
    * Writes a DataStream to the file specified by path in text format. For
    * every element of the DataStream the result of .toString is written.
    *
    * @param path The path pointing to the location the text file is written to
    * @param writeMode Controls the behavior for existing files. Options are NO_OVERWRITE and
    *                  OVERWRITE.
    * @return The closed DataStream
    */
  def writeAsText(path: String, writeMode: FileSystem.WriteMode): DataStreamSink[T] = {
    if (writeMode != null) {
      stream.writeAsText(path, writeMode)
    } else {
      stream.writeAsText(path)
    }
  }

  /**
    * Writes a DataStream to the file specified by path in text format. The writing is performed
    * periodically every millis milliseconds. For every element of the DataStream the result of
    * .toString is written.
    *
    * @param path The path pointing to the location the text file is written to
    * @param writeMode Controls the behavior for existing files. Options are NO_OVERWRITE and
    *                  OVERWRITE.
    * @param millis The file update frequency
    * @return The closed DataStream
    */
  def writeAsText(
      path: String,
      writeMode: FileSystem.WriteMode,
      millis: Long)
    : DataStreamSink[T] = {
    if (writeMode != null) {
      stream.writeAsText(path, writeMode, millis)
    } else {
      stream.writeAsText(path, millis)
    }
  }

  /**
    * Writes the DataStream in CSV format to the file specified by the path parameter. The writing
    * is performed periodically every millis milliseconds.
    *
    * @param path Path to the location of the CSV file
    * @return The closed DataStream
    */
  def writeAsCsv(path: String): DataStreamSink[T] = {
    writeAsCsv(
      path,
      null,
      0L,
      ScalaCsvOutputFormat.DEFAULT_LINE_DELIMITER,
      ScalaCsvOutputFormat.DEFAULT_FIELD_DELIMITER)
  }

  /**
    * Writes the DataStream in CSV format to the file specified by the path parameter. The writing
    * is performed periodically every millis milliseconds.
    *
    * @param path Path to the location of the CSV file
    * @param millis File update frequency
    * @return The closed DataStream
    */
  def writeAsCsv(path: String, millis: Long): DataStreamSink[T] = {
    writeAsCsv(
      path,
      null,
      millis,
      ScalaCsvOutputFormat.DEFAULT_LINE_DELIMITER,
      ScalaCsvOutputFormat.DEFAULT_FIELD_DELIMITER)
  }

  /**
    * Writes the DataStream in CSV format to the file specified by the path parameter. The writing
    * is performed periodically every millis milliseconds.
    *
    * @param path Path to the location of the CSV file
    * @param writeMode Controls whether an existing file is overwritten or not
    * @return The closed DataStream
    */
  def writeAsCsv(path: String, writeMode: FileSystem.WriteMode): DataStreamSink[T] = {
    writeAsCsv(
      path,
      writeMode,
      0L,
      ScalaCsvOutputFormat.DEFAULT_LINE_DELIMITER,
      ScalaCsvOutputFormat.DEFAULT_FIELD_DELIMITER)
  }

  /**
    * Writes the DataStream in CSV format to the file specified by the path parameter. The writing
    * is performed periodically every millis milliseconds.
    *
    * @param path Path to the location of the CSV file
    * @param writeMode Controls whether an existing file is overwritten or not
    * @param millis File update frequency
    * @return The closed DataStream
    */
  def writeAsCsv(path: String, writeMode: FileSystem.WriteMode, millis: Long): DataStreamSink[T] = {
    writeAsCsv(
      path,
      writeMode,
      millis,
      ScalaCsvOutputFormat.DEFAULT_LINE_DELIMITER,
      ScalaCsvOutputFormat.DEFAULT_FIELD_DELIMITER)
  }

  /**
    * Writes the DataStream in CSV format to the file specified by the path parameter. The writing
    * is performed periodically every millis milliseconds.
    *
    * @param path Path to the location of the CSV file
    * @param writeMode Controls whether an existing file is overwritten or not
    * @param millis File update frequency
    * @param rowDelimiter Delimiter for consecutive rows
    * @param fieldDelimiter Delimiter for consecutive fields
    * @return The closed DataStream
    */
  def writeAsCsv(
      path: String,
      writeMode: FileSystem.WriteMode,
      millis: Long,
      rowDelimiter: String,
      fieldDelimiter: String)
    : DataStreamSink[T] = {
    require(stream.getType.isTupleType, "CSV output can only be used with Tuple DataSets.")
    val of = new ScalaCsvOutputFormat[Product](new Path(path), rowDelimiter, fieldDelimiter)
    if (writeMode != null) {
      of.setWriteMode(writeMode)
    }
    stream.write(of.asInstanceOf[OutputFormat[T]], millis)
  }

  /**
   * Writes a DataStream using the given [[OutputFormat]]. The
   * writing is performed periodically, in every millis milliseconds.
   */
  def write(format: OutputFormat[T], millis: Long): DataStreamSink[T] = {
    stream.write(format, millis)
  }

  /**
   * Writes the DataStream to a socket as a byte array. The format of the output is
   * specified by a [[SerializationSchema]].
   */
  def writeToSocket(
      hostname: String,
      port: Integer,
      schema: SerializationSchema[T]): DataStreamSink[T] = {
    stream.writeToSocket(hostname, port, schema)
  }

  /**
   * Adds the given sink to this DataStream. Only streams with sinks added
   * will be executed once the StreamExecutionEnvironment.execute(...)
   * method is called.
   *
   */
  def addSink(sinkFunction: SinkFunction[T]): DataStreamSink[T] =
    stream.addSink(sinkFunction)

  /**
   * Adds the given sink to this DataStream. Only streams with sinks added
   * will be executed once the StreamExecutionEnvironment.execute(...)
   * method is called.
   *
   */
  def addSink(fun: T => Unit): DataStreamSink[T] = {
    if (fun == null) {
      throw new NullPointerException("Sink function must not be null.")
    }
    val cleanFun = clean(fun)
    val sinkFunction = new SinkFunction[T] {
      def invoke(in: T) = cleanFun(in)
    }
    this.addSink(sinkFunction)
  }

  /**
   * Returns a "closure-cleaned" version of the given function. Cleans only if closure cleaning
   * is not disabled in the [[org.apache.flink.api.common.ExecutionConfig]].
   */
  private[flink] def clean[F <: AnyRef](f: F): F = {
    new StreamExecutionEnvironment(stream.getExecutionEnvironment).scalaClean(f)
  }

}
