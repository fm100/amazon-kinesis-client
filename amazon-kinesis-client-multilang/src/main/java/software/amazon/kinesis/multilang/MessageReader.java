/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the
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
package software.amazon.kinesis.multilang;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import software.amazon.kinesis.multilang.messages.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides methods for interacting with the child process's STDOUT.
 * 
 * {@link #getNextMessageFromSTDOUT()} reads lines from the child process's STDOUT and attempts to decode a
 * {@link Message} object from each line. A child process's STDOUT could have lines that don't contain data related to
 * the multi-language protocol, such as when the child process prints debugging information to its STDOUT (instead of
 * logging to a file), also when a child processes writes a Message it is expected to prepend and append a new line
 * character to their message to help ensure that it is isolated on a line all by itself which results in empty lines
 * being present in STDOUT. Lines which cannot be decoded to a Message object are ignored.
 * 
 * {@link #drainSTDOUT()} simply reads all data from the child process's STDOUT until the stream is closed.
 */
class MessageReader {

    private BufferedReader reader;

    private String shardId;

    private ObjectMapper objectMapper;

    private ExecutorService executorService;

    /**
     * Use the initialize methods after construction.
     */
    MessageReader() {
    }

    /**
     * Returns a future which represents an attempt to read the next message in the child process's STDOUT. If the task
     * is successful, the result of the future will be the next message found in the child process's STDOUT, if the task
     * is unable to find a message before the child process's STDOUT is closed, or reading from STDOUT causes an
     * IOException, then an execution exception will be generated by this future.
     * 
     * The task employed by this method reads from the child process's STDOUT line by line. The task attempts to decode
     * each line into a {@link Message} object. Lines that fail to decode to a Message are ignored and the task
     * continues to the next line until it finds a Message.
     * 
     * @return
     */
    Future<Message> getNextMessageFromSTDOUT() {
        GetNextMessageTask getNextMessageTask = new GetNextMessageTask(objectMapper);
        getNextMessageTask.initialize(reader, shardId);
        return executorService.submit(getNextMessageTask);
    }

    /**
     * Returns a future that represents a computation that drains the STDOUT of the child process. That future's result
     * is true if the end of the child's STDOUT is reached, its result is false if there was an error while reading from
     * the stream. This task will log all the lines it drains to permit debugging.
     * 
     * @return
     */
    Future<Boolean> drainSTDOUT() {
        DrainChildSTDOUTTask drainTask = new DrainChildSTDOUTTask();
        drainTask.initialize(reader, shardId);
        return this.executorService.submit(drainTask);
    }

    /**
     * An initialization method allows us to delay setting the attributes of this class. Some of the attributes,
     * stream and shardId, are not known to the {@link MultiLangRecordProcessorFactory} when it constructs a
     * {@link MultiLangShardRecordProcessor} but are later determined when
     * {@link MultiLangShardRecordProcessor#initialize(String)} is called. So we follow a pattern where the attributes are
     * set inside this method instead of the constructor so that this object will be initialized when all its attributes
     * are known to the record processor.
     * 
     * @param stream Used to read messages from the subprocess.
     * @param shardId The shard we're working on.
     * @param objectMapper The object mapper to decode messages.
     * @param executorService An executor service to run tasks in.
     */
    MessageReader initialize(InputStream stream,
            String shardId,
            ObjectMapper objectMapper,
            ExecutorService executorService) {
        return this.initialize(new BufferedReader(new InputStreamReader(stream)), shardId, objectMapper,
                executorService);

    }

    /**
     * @param reader Used to read messages from the subprocess.
     * @param shardId The shard we're working on.
     * @param objectMapper The object mapper to decode messages.
     * @param executorService An executor service to run tasks in.
     */
    MessageReader initialize(BufferedReader reader,
            String shardId,
            ObjectMapper objectMapper,
            ExecutorService executorService) {
        this.reader = reader;
        this.shardId = shardId;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
        return this;
    }
}
