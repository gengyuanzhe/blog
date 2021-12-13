# betwixt切换到XStream

## 切换策略

### cmsgw

- cmsgw定制点

	- 反序列化时：betwixt会调用setter方法，对于collection会指定updater来构建bean
序列化时：betwixt会调用getter方法
	- 所有的基本类型都要先trim()，另外数值转换失败需要抛出CMSGWException

- 难点

	- 如何用XStream表示多层关系

		- <ADI>           XmlAdiObject
 <Asset> 
  <Metadata>    AdiMeta
  <Asset>       AdiAsset

	- XStream如何定制自定义转换
	- 对于基本类型有额外的处理流程
	- xstream并不会调用getter和setter方法，也无法对collection指定updater

- 切换策略

	- 自定义converter
	- adapter层

		- 先构造出xstream易于转换的bean，再把该bean转化为cmsgw的bean

### igw

- 辉姐初步分析igw中betwixt使用较为简单，不涉及setter getter updater，应该只需要配置注解进行映射切换即可

### epggw

- 当前策略是不切换到xstream，张洁负责把v2的接口拆出来

## betwixt知识点

### betwixt依赖bean的getter和setter方法，也可以指定updater

### betwxit的基本配置可以参考类org.apache.commons.betwixt.IntrospectionConfiguration进行设置

- isWrapCollectionsInElement 表示是否在复合属性外再包一层element

### 定制字段名映射策略

- 实现org.apache.commons.betwixt.strategy.NameMapper，.betwixt覆盖代码中XMLIntrospector中的配置
- 设置属性名映射：writer.getXMLIntrospector().setAttributeNameMapper(new HyphenatedNameMapper());
- 设置元素名映射：writer.getXMLIntrospector().setElementNameMapper(new DecapitalizeNameMapper());


### 定制复合属性映射策略

- 实现org.apache.commons.betwixt.PluralStemmer
- 集合/map，集合会搜索单个参数的add方法，map会搜索两个参数的add方法

### 控制映射为attribute还是element

- 直接设置writer.getXMLIntrospector().getConfiguration().setAttributesForPrimitives (true);
- 定制SimpleTypeMapper，并设置introspector.getConfiguration().setSimpleTypeMapper(new StringsAsElementsSimpleTypeMapper()); 

### 定制忽略策略

- beanWriter.getXMLIntrospector().getConfiguration().setPropertySuppressionStrategy(
        new PropertySuppressionStrategy() {
             public boolean suppressProperty(Class classContainingTheProperty,
                     Class propertyType, String propertyName) {
                 if (Class.class.equals(propertyType) 
                         && "class".equals(propertyName)) { 
                     if (!Throwable.class
                                .isAssignableFrom(classContainingTheProperty)) {
                        return true;
                     }
                 }
                 return false;
             }
         });

### 指定updater，访问非public方法

- <element
    name='SomeName' 
    property='someProperty' 
    updater='setSomeProperty' <!-- this attribute must be present -->
    forceAccessible="true"/>  <!-- for this line to work -->

### 定制String-Object转换规则

- 继承org.apache.commons.betwixt.strategy.ObjectStringConverter，或者继承org.apache.commons.betwixt.strategy.ConvertUtilsObjectStringConverter，并设置reader.getBindingConfiguration().setObjectStringConverter(new CustomConvertUtilsObjectStringConverter());

### multiMapping

- 也就是在一个.betwixt文件中配置多个映射，通过class来区分

## xstream知识点

### xstream不关心field的可见性，同时他也不要求bean有getter和setter以及默认构造函数，当然他在marshall和unmarshall的时候也不会调用getter、setter、updater以及constructor

### 选择底层xml解析实现

- XStream xstream = new XStream(new DomDriver()); // does not require XPP3 library
- XStream xstream = new XStream(new StaxDriver()); // does not require XPP3 library starting with Java 6

### 序列化/反序列化

- xstream.toXML(Object obj);
- xstream.fromXML(String xml);

### 别名配置

- class alias

	- xstream.alias(String elementName, Class cls);

- field alias

	- xstream.aliasField("author", Blog.class, "writer");

- Implicit Collections

	- xstream.addImplicitCollection(Blog.class, "entries");

- Attribute aliasing

	- xstream.useAttributeFor(Blog.class, "writer");
xstream.aliasField("author", Blog.class, "writer");
	- 如何把一个复杂的类转化成String，作为attribute的值

		- 继承SingleValueConverter并register 

			- class AuthorConverter implements SingleValueConverter {
        public String toString(Object obj) {
                return ((Author) obj).getName();
        }
        public Object fromString(String name) {
                return new Author(name);
        }
        public boolean canConvert(Class type) {
                return type.equals(Author.class);
        }
}
			- xstream.registerConverter(new AuthorConverter());

	- attribute的值通常会被去掉leading和trailing的空格

- package aliasing

	- xstream.aliasPackage("my.company", "org.thoughtworks");

### 自定义converter

- 实现Converter 

	- 返回是否对该类做转换：canConvert(Class clazz) 
	- object to xml： marshal(Object value, HierarchicalStreamWriter writer,
MarshallingContext context)
	- xml to object：unmarshal(HierarchicalStreamReader reader,
UnmarshallingContext context)

### 注解

- @XStreamAlias
- @XStreamImplicit

	- @XStreamImplicit(itemFieldName="part")

- @XStreamConverter(SingleValueCalendarConverter.class)

	- @XStreamConverter(value=BooleanConverter.class, booleans={false}, strings={"yes", "no"})

- @XStreamAsAttribute
- @XStreamOmitField

*XMind: ZEN - Trial Version*