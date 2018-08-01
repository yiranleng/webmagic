package us.codecraft.webmagic.model;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.model.annotation.*;
import us.codecraft.webmagic.model.formatter.ObjectFormatter;
import us.codecraft.webmagic.model.formatter.ObjectFormatterBuilder;
import us.codecraft.webmagic.selector.*;
import us.codecraft.webmagic.utils.ClassUtils;
import us.codecraft.webmagic.utils.ExtractorUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static us.codecraft.webmagic.model.annotation.ExtractBy.Source.RawText;

/**
 * The main internal logic of page model extractor.
 *
 * @author code4crafter@gmail.com <br>
 * @since 0.2.0
 */
class PageModelExtractor {

    private List<Pattern> targetUrlPatterns = new ArrayList<Pattern>();

    private Selector targetUrlRegionSelector;

    private List<Pattern> helpUrlPatterns = new ArrayList<Pattern>();

    private Selector helpUrlRegionSelector;

    private Class clazz;

    private List<FieldExtractor> fieldExtractors;

    private Extractor pageExtractor;

    private Logger logger = LoggerFactory.getLogger(getClass());

    public static PageModelExtractor create(Class clazz) {
        PageModelExtractor pageModelExtractor = new PageModelExtractor();
        pageModelExtractor.init(clazz);
        return pageModelExtractor;
    }

    private void init(Class clazz) {
        this.clazz = clazz;
        initClassExtractors();
        fieldExtractors = new ArrayList<FieldExtractor>();
        for (Field field : ClassUtils.getFieldsIncludeSuperClass(clazz)) {
            field.setAccessible(true);
            FieldExtractor fieldExtractor = getAnnotationExtractBy(clazz, field);
            FieldExtractor fieldExtractorTmp = getAnnotationExtractCombo(clazz, field);
            if (fieldExtractor != null && fieldExtractorTmp != null) {
                throw new IllegalStateException("Only one of 'ExtractBy ComboExtract ExtractByUrl' can be added to a field!");
            } else if (fieldExtractor == null && fieldExtractorTmp != null) {
                fieldExtractor = fieldExtractorTmp;
            }
            fieldExtractorTmp = getAnnotationExtractByUrl(clazz, field);
            if (fieldExtractor != null && fieldExtractorTmp != null) {
                throw new IllegalStateException("Only one of 'ExtractBy ComboExtract ExtractByUrl' can be added to a field!");
            } else if (fieldExtractor == null && fieldExtractorTmp != null) {
                fieldExtractor = fieldExtractorTmp;
            }
            if (fieldExtractor != null) {
                fieldExtractor.setObjectFormatter(new ObjectFormatterBuilder().setField(field).build());
                fieldExtractors.add(fieldExtractor);
            }
        }
    }

    private FieldExtractor getAnnotationExtractByUrl(Class clazz, Field field) {
        FieldExtractor fieldExtractor = null;
        ExtractByUrl extractByUrl = field.getAnnotation(ExtractByUrl.class);
        if (extractByUrl != null) {
            String regexPattern = extractByUrl.value();
            if (regexPattern.trim().equals("")) {
                regexPattern = ".*";
            }
            fieldExtractor = new FieldExtractor(field,
                    new RegexSelector(regexPattern), FieldExtractor.Source.Url, extractByUrl.notNull(),
                    extractByUrl.multi() || List.class.isAssignableFrom(field.getType()));
            Method setterMethod = getSetterMethod(clazz, field);
            if (setterMethod != null) {
                fieldExtractor.setSetterMethod(setterMethod);
            }
        }
        return fieldExtractor;
    }

    private FieldExtractor getAnnotationExtractCombo(Class clazz, Field field) {
        FieldExtractor fieldExtractor = null;
        ComboExtract comboExtract = field.getAnnotation(ComboExtract.class);
        if (comboExtract != null) {
            ExtractBy[] extractBies = comboExtract.value();
            Selector selector;
            switch (comboExtract.op()) {
                case And:
                    selector = new AndSelector(ExtractorUtils.getSelectors(extractBies));
                    break;
                case Or:
                    selector = new OrSelector(ExtractorUtils.getSelectors(extractBies));
                    break;
                default:
                    selector = new AndSelector(ExtractorUtils.getSelectors(extractBies));
            }
            fieldExtractor = new FieldExtractor(field, selector, comboExtract.source() == ComboExtract.Source.RawHtml ? FieldExtractor.Source.RawHtml : FieldExtractor.Source.Html,
                    comboExtract.notNull(), comboExtract.multi() || List.class.isAssignableFrom(field.getType()));
            Method setterMethod = getSetterMethod(clazz, field);
            if (setterMethod != null) {
                fieldExtractor.setSetterMethod(setterMethod);
            }
        }
        return fieldExtractor;
    }
    

    private FieldExtractor getAnnotationExtractBy(Class clazz, Field field) {
        FieldExtractor fieldExtractor = null;
        ExtractBy extractBy = field.getAnnotation(ExtractBy.class);
        if (extractBy != null) {
            Selector selector = ExtractorUtils.getSelector(extractBy);
            FieldExtractor.Source source = getExtractorSource(extractBy);

            fieldExtractor = new FieldExtractor(field, selector, source,
                    extractBy.notNull(), List.class.isAssignableFrom(field.getType()));
            fieldExtractor.setSetterMethod(getSetterMethod(clazz, field));
        }
        return fieldExtractor;
    }
    
    
    /**
     * 类型转换
     * @param source0
     * @return
     */
	private FieldExtractor.Source getExtractorSource(ExtractBy extractBy) {
		ExtractBy.Source source0 = extractBy.source();
		FieldExtractor.Source source = null;
		if (extractBy.type() == ExtractBy.Type.JsonPath) source0 = RawText;
		switch (source0) {
		case RawText:
			source = FieldExtractor.Source.RawText;
			break;
		case RawHtml:
			source = FieldExtractor.Source.RawHtml;
			break;
		case SelectedHtml:
			source = FieldExtractor.Source.Html;
			break;
		default:
			source = FieldExtractor.Source.Html;
		}
		return source;
	}
    

    public static Method getSetterMethod(Class clazz, Field field) {
        String name = "set" + StringUtils.capitalize(field.getName());
        try {
            Method declaredMethod = clazz.getDeclaredMethod(name, field.getType());
            declaredMethod.setAccessible(true);
            return declaredMethod;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private void initClassExtractors() {
        Annotation annotation = clazz.getAnnotation(TargetUrl.class);
        if (annotation == null) {
            targetUrlPatterns.add(Pattern.compile(".*"));
        } else {
            TargetUrl targetUrl = (TargetUrl) annotation;
            String[] value = targetUrl.value();
            for (String s : value) {
                targetUrlPatterns.add(Pattern.compile(s.replace(".", "\\.").replace("*", "[^\"'#]*")));
            }
            if (!targetUrl.sourceRegion().equals("")) {
                targetUrlRegionSelector = new XpathSelector(targetUrl.sourceRegion());
            }
        }
        annotation = clazz.getAnnotation(HelpUrl.class);
        if (annotation != null) {
            HelpUrl helpUrl = (HelpUrl) annotation;
            String[] value = helpUrl.value();
            for (String s : value) {
                helpUrlPatterns.add(Pattern.compile(s.replace(".", "\\.").replace("*", "[^\"'#]*")));
            }
            if (!helpUrl.sourceRegion().equals("")) {
                helpUrlRegionSelector = new XpathSelector(helpUrl.sourceRegion());
            }
        }
        annotation = clazz.getAnnotation(ExtractBy.class);
        if (annotation != null) {
            ExtractBy extractBy = (ExtractBy) annotation;
            //wuxiaoxu class 不支持css
//            pageExtractor = new Extractor(new XpathSelector(extractBy.value()), Extractor.Source.Html, extractBy.notNull(), extractBy.multi());
            pageExtractor = new Extractor(ExtractorUtils.getSelector(extractBy), getExtractorSource(extractBy), extractBy.notNull(), extractBy.multi());
        }
    }

    
    /**
     * 开始处理数据
     * @param page
     * @return
     */
    public Object process(Page page) {
    	if (!isMatch(page, targetUrlPatterns)) return null;
        if (null == pageExtractor) return processSingle(page, null, true);

        if (pageExtractor.multi) {
            List<Object> os = new ArrayList<Object>();
            List<String> list = pageExtractor.getSelector().selectList(page.getRawText());
            for (String s : list) {
                Object o = processSingle(page, s, false);
                if (o != null) {
                    os.add(o);
                }
            }
            return os;
        } 
        String select = pageExtractor.getSelector().select(page.getRawText());
        Object o = processSingle(page, select, false);
        return o;
    }
    
    
    /**
     * 检查当前html页面url是否符合urlpattern
     * @param page
     * @param targetUrlPatterns
     * @return
     */
	public boolean isMatch(Page page, List<Pattern> targetUrlPatterns) {
		for (Pattern targetPattern : targetUrlPatterns) {
			if (!targetPattern.matcher(page.getUrl().toString()).matches()) continue;
			return true;
		}
		return false;
	}

    private Object processSingle(Page page, String html, boolean isRaw) {
        Object o = null;
        try {
            o = clazz.newInstance();
            for (FieldExtractor fieldExtractor : fieldExtractors) {
                if (fieldExtractor.isMulti()) {
                	o = parseMultiField(fieldExtractor, page, html, isRaw, o);
                } else {
                	o = parseField(fieldExtractor, page, html, isRaw, o);
                }
            }
            if (AfterExtractor.class.isAssignableFrom(clazz)) {
                ((AfterExtractor) o).afterProcess(page);
            }
        } catch (InstantiationException e) {
            logger.error("extract fail", e);
        } catch (IllegalAccessException e) {
            logger.error("extract fail", e);
        } catch (InvocationTargetException e) {
            logger.error("extract fail", e);
        }
        return o;
    }
    
    
    /**
     * 解析list类型的属性数据
     * @param fieldExtractor
     * @param page
     * @param html
     * @param isRaw
     * @param o
     * @return
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private Object parseMultiField(FieldExtractor fieldExtractor, Page page, String html, boolean isRaw , Object o) throws IllegalAccessException, InvocationTargetException{
        List<String> value;
        switch (fieldExtractor.getSource()) {
            case RawHtml:
                value = page.getHtml().selectDocumentForList(fieldExtractor.getSelector());
                break;
            case Html:
                if (isRaw) {
                    value = page.getHtml().selectDocumentForList(fieldExtractor.getSelector());
                } else {
                    value = fieldExtractor.getSelector().selectList(html);
                }
                break;
            case Url:
                value = fieldExtractor.getSelector().selectList(page.getUrl().toString());
                break;
            case RawText:
                value = fieldExtractor.getSelector().selectList(page.getRawText());
                break;
            default:
                value = fieldExtractor.getSelector().selectList(html);
        }
        if ((value == null || value.size() == 0) && fieldExtractor.isNotNull()) {
            return null;
        }
        if (fieldExtractor.getObjectFormatter() != null) {
            List<Object> converted = convert(value, fieldExtractor.getObjectFormatter());
            setField(o, fieldExtractor, converted);
        } else {
            setField(o, fieldExtractor, value);
        }
        return o;
    }
    
    /**
     * 解析单个类型的属性数据
     * @param fieldExtractor
     * @param page
     * @param html
     * @param isRaw
     * @param o
     * @return
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private Object parseField(FieldExtractor fieldExtractor, Page page, String html, boolean isRaw , Object o) throws IllegalAccessException, InvocationTargetException{
        String value;
        switch (fieldExtractor.getSource()) {
            case RawHtml:
                value = page.getHtml().selectDocument(fieldExtractor.getSelector());
                break;
            case Html:
                if (isRaw) {
                    value = page.getHtml().selectDocument(fieldExtractor.getSelector());
                } else {
                    value = fieldExtractor.getSelector().select(html);
                }
                break;
            case Url:
                value = fieldExtractor.getSelector().select(page.getUrl().toString());
                break;
            case RawText:
                value = fieldExtractor.getSelector().select(page.getRawText());
                break;
            default:
                value = fieldExtractor.getSelector().select(html);
        }
        if (value == null && fieldExtractor.isNotNull()) {
            return null;
        }
        if (fieldExtractor.getObjectFormatter() != null) {
            Object converted = convert(value, fieldExtractor.getObjectFormatter());
            if (converted == null && fieldExtractor.isNotNull()) {
                return null;
            }
            setField(o, fieldExtractor, converted);
        } else {
            setField(o, fieldExtractor, value);
        }
    	return o;
    }
    
    

    private Object convert(String value, ObjectFormatter objectFormatter) {
        try {
            Object format = objectFormatter.format(value);
            logger.debug("String {} is converted to {}", value, format);
            return format;
        } catch (Exception e) {
            logger.error("convert " + value + " to " + objectFormatter.clazz() + " error!", e);
        }
        return null;
    }

    private List<Object> convert(List<String> values, ObjectFormatter objectFormatter) {
        List<Object> objects = new ArrayList<Object>();
        for (String value : values) {
            Object converted = convert(value, objectFormatter);
            if (converted != null) {
                objects.add(converted);
            }
        }
        return objects;
    }

    private void setField(Object o, FieldExtractor fieldExtractor, Object value) throws IllegalAccessException, InvocationTargetException {
        if (value == null) {
            return;
        }
        if (fieldExtractor.getSetterMethod() != null) {
            fieldExtractor.getSetterMethod().invoke(o, value);
        }
        fieldExtractor.getField().set(o, value);
    }

    Class getClazz() {
        return clazz;
    }

    List<Pattern> getTargetUrlPatterns() {
        return targetUrlPatterns;
    }

    List<Pattern> getHelpUrlPatterns() {
        return helpUrlPatterns;
    }

    Selector getTargetUrlRegionSelector() {
        return targetUrlRegionSelector;
    }

    Selector getHelpUrlRegionSelector() {
        return helpUrlRegionSelector;
    }
}
