1. 当value为空时，通过Stream的Collectors.toMap方法构造map时，会抛出空指针异常，见<https://stackoverflow.com/questions/24630963/nullpointerexception-in-collectors-tomap-with-null-entry-values>，这个是JDK的bug，可以使用如下下发解决

```java
Map<Integer, Boolean> collect = list.stream().
    collect(HashMap::new, (m,v)->m.put(v.getId(), v.getAnswer()), HashMap::putAll);
```
