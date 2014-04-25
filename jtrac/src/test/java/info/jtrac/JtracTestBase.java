package info.jtrac;

import info.jtrac.repository.JtracDao;
import info.jtrac.service.Jtrac;

import java.io.File;

import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;


/**
 * base class for tests that can test either the service layer or dao or both
 * using the Spring JUnit helper class with the long name, ensures that
 * the applicationContext is only built once
 */
@ContextConfiguration(locations = {
		"file:src/main/webapp/WEB-INF/applicationContext.xml",
		"file:src/main/webapp/WEB-INF/applicationContext-lucene.xml"
})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public abstract class JtracTestBase extends AbstractTransactionalJUnit4SpringContextTests {

	private static final String JTRAC_HOME_KEY = "jtrac.home";
	private static final String JTRAC_HOME_VALUE = "target/home";
	private static final File JTRAC_HOME_DIRECTORY = new File(JTRAC_HOME_VALUE);


	protected Jtrac jtrac;
	protected JtracDao dao;
	protected JdbcTemplate jdbcTemplate;

	public JtracTestBase() {
		System.setProperty(JTRAC_HOME_KEY, JTRAC_HOME_VALUE);
	}

	@Autowired
	public void setDao(JtracDao dao) {
		this.dao = dao;
	}

	@Override
	@Autowired
	public void setDataSource(DataSource dataSource) {
		super.setDataSource(dataSource);
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Autowired
	public void setJtrac(Jtrac jtrac) {
		this.jtrac = jtrac;
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		FileUtils.deleteDirectory(JTRAC_HOME_DIRECTORY);
	}



	@AfterClass
	public static void afterClass() throws Exception {
		// context is closed after this method is called
		// FileUtils.deleteDirectory(JTRAC_HOME_DIRECTORY);
	}

}
