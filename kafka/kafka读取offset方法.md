(未完待续)

1. 获取topicPartion

    ```scala
    val consumerGroups = describeConsumerGroups(groupIds)
    上面的结果consumerGroups是个map, key是groupid, value是ConsumerGroupDescription.members.assignment.topicPartitions 拿到topicPartions集合
    ```

2. 拿到当前的offset
下面方法拿到current的offset

    ```scala
    adminClient.listConsumerGroupOffsets(
            groupId,
            withTimeoutMs(new ListConsumerGroupOffsetsOptions)
        ).partitionsToOffsetAndMetadata
    ```

3. 拿到endOffset
下面代码拿到endOffsets

    ```scala
    val endOffsets = topicPartitions.map { topicPartition =>
            topicPartition -> OffsetSpec.latest
        }.toMap
        val offsets = adminClient.listOffsets(
            endOffsets.asJava,
            withTimeoutMs(new ListOffsetsOptions)
        ).all
    ```
