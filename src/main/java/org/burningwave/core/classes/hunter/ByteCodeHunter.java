package org.burningwave.core.classes.hunter;

import java.util.function.Supplier;

import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.hunter.SearchCriteriaAbst.TestContext;
import org.burningwave.core.io.FileInputStream;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemHelper.Scan;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.io.ZipInputStream;

public class ByteCodeHunter extends CacherHunter<String, JavaClass, SearchContext<String, JavaClass>, SearchResult<String, JavaClass>> {
	
	private ByteCodeHunter(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper,
		PathHelper pathHelper,
		StreamHelper streamHelper,
		ClassHelper classHelper,
		MemberFinder memberFinder
	) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			fileSystemHelper,
			pathHelper,
			streamHelper, 
			classHelper,
			memberFinder,
			(initContext) -> SearchContext.<String, JavaClass>create(
				fileSystemHelper, streamHelper, initContext
			),
			(context) -> new SearchResult<String, JavaClass>(context)
		);
	}
	
	public static ByteCodeHunter create(Supplier<ByteCodeHunter> byteCodeHunterSupplier, Supplier<ClassHunter> classHunterSupplier, 
		FileSystemHelper fileSystemHelper, PathHelper pathHelper, StreamHelper streamHelper,
		ClassHelper classHelper, MemberFinder memberFinder
	) {
		return new ByteCodeHunter(byteCodeHunterSupplier, classHunterSupplier, fileSystemHelper, pathHelper, streamHelper, classHelper, memberFinder);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	<S extends SearchCriteriaAbst<S>> TestContext<S> testCriteria(SearchContext<String, JavaClass> context, JavaClass javaClass) {
		return context.getCriteria().hasNoPredicate() ?
			(TestContext<S>)context.getCriteria().testAndReturnTrueIfNullOrTrueByDefault(null) :
			super.testCriteria(context, javaClass);
	}
	
	@Override
	<S extends SearchCriteriaAbst<S>> TestContext<S> testCachedItem(SearchContext<String, JavaClass> context, String path, String key, JavaClass javaClass) {
		return super.testCriteria(context, javaClass);
	}
	
	@Override
	void retrieveItemFromFileInputStream(
		SearchContext<String, JavaClass> context, 
		TestContext<SearchCriteria> criteriaTestContext,
		Scan.ItemContext<FileInputStream> scanItemContext,
		JavaClass javaClass
	) {
		context.addItemFound(scanItemContext.getBasePathAsString(), scanItemContext.getInput().getAbsolutePath(), javaClass);
	}

	
	@Override
	void retrieveItemFromZipEntry(
		SearchContext<String, JavaClass> context,
		TestContext<SearchCriteria> criteriaTestContext,
		Scan.ItemContext<ZipInputStream.Entry> scanItemContext,
		JavaClass javaClass) {
		context.addItemFound(scanItemContext.getBasePathAsString(), scanItemContext.getInput().getAbsolutePath(), javaClass);
	}
}
