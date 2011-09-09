package oimsamples.scripts;

import java.util.HashMap;
import java.util.Properties;
import java.util.Random;

import oimsamples.util.OIMUtils;


import Thor.API.tcResultSet;
import Thor.API.tcUtilityFactory;
import Thor.API.Operations.tcFormDefinitionOperationsIntf;
import Thor.API.Operations.tcFormInstanceOperationsIntf;
import Thor.API.Operations.tcITResourceDefinitionOperationsIntf;
import Thor.API.Operations.tcITResourceInstanceOperationsIntf;
import Thor.API.Operations.tcLookupOperationsIntf;
import Thor.API.Operations.tcObjectOperationsIntf;
import Thor.API.Operations.tcUserOperationsIntf;

import com.thortech.xl.util.config.ConfigurationClient;

public class DummyResourceSetup {
	public static void main(String[] args) throws Exception {
		int count = Integer.parseInt(args[0].trim());
		System.out.println("count = " + count);
		Random r = new Random(System.currentTimeMillis());
		
		
		Properties jndi = ConfigurationClient.getComplexSettingByPath("Discovery.CoreServer").getAllSettings();
		tcUtilityFactory factory = new tcUtilityFactory(jndi, "xelsysadm", "xelsysadm");
		tcObjectOperationsIntf objIntf = (tcObjectOperationsIntf) factory.getUtility("Thor.API.Operations.tcObjectOperationsIntf;");
		tcUserOperationsIntf  usrIntf = (tcUserOperationsIntf) factory.getUtility("Thor.API.Operations.tcUserOperationsIntf;");
		tcITResourceDefinitionOperationsIntf itdefIntf = (tcITResourceDefinitionOperationsIntf) factory.getUtility("Thor.API.Operations.tcITResourceDefinitionOperationsIntf;");
	    tcITResourceInstanceOperationsIntf itinstIntf = (tcITResourceInstanceOperationsIntf) factory.getUtility("Thor.API.Operations.tcITResourceInstanceOperationsIntf;");
	    tcFormDefinitionOperationsIntf fdIntf = (tcFormDefinitionOperationsIntf) factory.getUtility("Thor.API.Operations.tcFormDefinitionOperationsIntf;");
	    tcFormInstanceOperationsIntf fiIntf = (tcFormInstanceOperationsIntf) factory.getUtility("Thor.API.Operations.tcFormInstanceOperationsIntf");
	    tcLookupOperationsIntf lookupIntf = (tcLookupOperationsIntf) factory.getUtility("Thor.API.Operations.tcLookupOperationsIntf");

	    System.out.println("logged in");
	    
		String objName = "Dummy Resource";
		String pForm = "UD_DUMMYP";
		String cForm = "UD_DUMMYC";
		String lookupName = "Lookup.DummyResource.Entitlements";
		
		String[] itResNames = {
			"dummy_itres1", "dummy_itres2", "dummy_itres3" 	
		};
		
		long objKey = OIMUtils.getObjectKey(objIntf, objName);
		System.out.println("objName = " + objName + " obj_key=" + objKey);
		long psdkKey = OIMUtils.getFormDefKey(fdIntf, pForm);
		System.out.println("pForm = " + pForm + " sdk_key=" + psdkKey);
		long csdkKey = OIMUtils.getFormDefKey(fdIntf, cForm);
		System.out.println("cForm = " + cForm + " sdk_key=" + csdkKey);
		
		long itResKeys[] = new long[itResNames.length];
		
		for (int i=0 ; i<itResNames.length ; i++) {
			String itresName = itResNames[i];
			long itresKey = OIMUtils.getITResKey(itinstIntf, itresName);
			itResKeys[i] = itresKey;
			System.out.println("itresName = " + itresName + " svr_key=" + itresKey);
		}
		
		// create lookup defs
		/*
		for (int i=0 ; i<100 ; i++) {
			HashMap hm = new HashMap();
			long itresKey = itResKeys[i%itResKeys.length];
			String encode = itresKey + "~ent_" + i;
			String decode = itresKey + "~desc_" + i;
			
			System.out.println("Adding lookup value = " + encode);
			lookupIntf.addLookupValue(lookupName, encode, decode, "en", "US");
		}
		*/
		
		// get entitlements
		tcResultSet rs1 = lookupIntf.getLookupValues(lookupName);
		String[] entitlements = new String[rs1.getRowCount()];
		for (int i=0 ; i<rs1.getRowCount() ; i++) {
			rs1.goToRow(i);
			String encode = rs1.getStringValue("Lookup Definition.Lookup Code Information.Code Key");
			entitlements[i] = encode;
		}

		// create the users
		long[] usrKeys = new long[count];
		int n = 0;
		while (n < count) {
			String first = FIRST_NAMES[r.nextInt(FIRST_NAMES.length)];
			String last = LAST_NAMES[r.nextInt(LAST_NAMES.length)];
			String usrLogin = first + "_" + last;
			
			System.out.println("Attempting to create user with login = " + usrLogin );
			
			boolean userExists = checkIfUserExists(usrIntf, usrLogin);
			if (!userExists) {
				long usrKey = createUser(usrIntf, usrLogin, first, last);
				usrKeys[n] = usrKey;
				n++;
				System.out.println("Created user with login = " + usrLogin + " usr_key=" + usrKey);
			} else {
				System.out.println("User with login = " + usrLogin + " exists");
			}
		}
		
        // provision the users with the resource
        for (int i=0 ; i<count ; i++) {
        	long itresKey = itResKeys[i%itResKeys.length];
        	long usrKey = usrKeys[i];
			//long usrKey = i;
        	
        	System.out.println("Provisioning user with key = " + usrKey + " with object " + objName);
        	
            long oiuKey = usrIntf.provisionObject(usrKey, objKey);
            long orcKey = 0;

            tcResultSet rs = usrIntf.getObjects(usrKey);

            for (int j=0 ; j<rs.getRowCount() ; j++) {
                rs.goToRow(j);
                long oiuKeyRS = rs.getLongValue("Users-Object Instance For User.Key");

                if (oiuKeyRS == oiuKey) {
                    orcKey = rs.getLongValue("Process Instance.Key");
                    System.out.println("Found orc_key = " + orcKey + " for oiu_key = " + oiuKey);
                    break;
                }
            }

            System.out.println("Setting it res for process form with orc_key = " + orcKey);

            HashMap hm = new HashMap();
            hm.put(pForm + "_ITRES", itresKey + "");

            fiIntf.setProcessFormData(orcKey, hm);

            System.out.println("Setting child data for process form with orc_key = " + orcKey);
            // now child table data
            for (int k=0 ; k<5 ; k++) {

                HashMap hm1 = new HashMap();
                String ent = entitlements[r.nextInt(entitlements.length)];
                hm1.put(cForm + "_PARENT", ent + "_parent");
                hm1.put(cForm + "_ENT", ent);
                hm1.put(cForm + "_DESC", ent + "_desc1_child1_data_" + k);

                fiIntf.addProcessFormChildData(csdkKey, orcKey, hm1);
                System.out.println("Provisioned entitlement " + ent + " to usr_key=" + usrKey);
            }
        }

        usrIntf.close();
        objIntf.close();
        itdefIntf.close();
        itinstIntf.close();
        fdIntf.close();
        fiIntf.close();
        lookupIntf.close();
        
        factory.close();
        System.exit(0);
	}
	
	private static long createUser(tcUserOperationsIntf usrIntf, 
			String userLogin, String first, String last)
       throws Exception {
       String email = first + "." + last + "@oracle.com";

       HashMap hm = new HashMap();
       hm.put("Users.User ID", userLogin);
       hm.put("Users.First Name", first);
       hm.put("Users.Last Name", last);
       hm.put("Users.Role","Full-Time");
       hm.put("Users.Xellerate Type","End-User");
       hm.put("Organizations.Key", "2");
       hm.put("Users.Password", "Welcome1");
       hm.put("Users.Email", email);

       long usr_key = usrIntf.createUser(hm);
       return usr_key;
   }

	private static boolean checkIfUserExists(tcUserOperationsIntf usrIntf, String userLogin)
    	throws Exception {
		boolean ret = false;
		HashMap hm = new HashMap();
		hm.put("Users.User ID", userLogin);

		tcResultSet rs = usrIntf.findUsersFiltered(hm, USR_COLUMNS);
		
		if (rs.getRowCount() > 0) {
			rs.goToRow(0);
			long usrKey = rs.getLongValue("Users.Key");
			String usrLoginFromRS = rs.getStringValue("Users.User ID");
			if (usrKey > 0 && userLogin.equalsIgnoreCase(usrLoginFromRS)) {
				ret = true;
			}
		}
		
		return ret;
	}	

	public static final String[] USR_COLUMNS = new String[] {
			"Users.User ID",
			"Users.Key",
			"Users.Row Version"
		};

	
	public static final String[] FIRST_NAMES = {
		"JAMES",
		"JOHN",
		"ROBERT",
		"MICHAEL",
		"WILLIAM",
		"DAVID",
		"RICHARD",
		"CHARLES",
		"JOSEPH",
		"THOMAS",
		"CHRISTOPHER",
		"DANIEL",
		"PAUL",
		"MARK",
		"DONALD",
		"GEORGE",
		"KENNETH",
		"STEVEN",
		"EDWARD",
		"BRIAN",
		"RONALD",
		"ANTHONY",
		"KEVIN",
		"JASON",
		"MATTHEW",
		"GARY",
		"TIMOTHY",
		"JOSE",
		"LARRY",
		"JEFFREY",
		"FRANK",
		"SCOTT",
		"ERIC",
		"STEPHEN",
		"ANDREW",
		"RAYMOND",
		"GREGORY",
		"JOSHUA",
		"JERRY",
		"DENNIS",
		"WALTER",
		"PATRICK",
		"PETER",
		"HAROLD",
		"DOUGLAS",
		"HENRY",
		"CARL",
		"ARTHUR",
		"RYAN",
		"ROGER",
		"JOE",
		"JUAN",
		"JACK",
		"ALBERT",
		"JONATHAN",
		"JUSTIN",
		"TERRY",
		"GERALD",
		"KEITH",
		"SAMUEL",
		"WILLIE",
		"RALPH",
		"LAWRENCE",
		"NICHOLAS",
		"ROY",
		"BENJAMIN",
		"BRUCE",
		"BRANDON",
		"ADAM",
		"HARRY",
		"FRED",
		"WAYNE",
		"BILLY",
		"STEVE",
		"LOUIS",
		"JEREMY",
		"AARON",
		"RANDY",
		"HOWARD",
		"EUGENE",
		"CARLOS",
		"RUSSELL",
		"BOBBY",
		"VICTOR",
		"MARTIN",
		"ERNEST",
		"PHILLIP",
		"TODD",
		"JESSE",
		"CRAIG",
		"ALAN",
		"SHAWN",
		"CLARENCE",
		"SEAN",
		"PHILIP",
		"CHRIS",
		"JOHNNY",
		"EARL",
		"JIMMY",
		"ANTONIO",
		"DANNY",
		"BRYAN",
		"TONY",
		"LUIS",
		"MIKE",
		"STANLEY",
		"LEONARD",
		"NATHAN",
		"DALE",
		"MANUEL",
		"RODNEY",
		"CURTIS",
		"NORMAN",
		"ALLEN",
		"MARVIN",
		"VINCENT",
		"GLENN",
		"JEFFERY",
		"TRAVIS",
		"JEFF",
		"CHAD",
		"JACOB",
		"LEE",
		"MELVIN",
		"ALFRED",
		"KYLE",
		"FRANCIS",
		"BRADLEY",
		"JESUS",
		"HERBERT",
		"FREDERICK",
		"RAY",
		"JOEL",
		"EDWIN",
		"DON",
		"EDDIE",
		"RICKY",
		"TROY",
		"RANDALL",
		"BARRY",
		"ALEXANDER",
		"BERNARD",
		"MARIO",
		"LEROY",
		"FRANCISCO",
		"MARCUS",
		"MICHEAL",
		"THEODORE",
		"CLIFFORD",
		"MIGUEL",
		"OSCAR",
		"JAY",
		"JIM",
		"TOM",
		"CALVIN",
		"ALEX",
		"JON",
		"RONNIE",
		"BILL",
		"LLOYD",
		"TOMMY",
		"LEON",
		"DEREK",
		"WARREN",
		"DARRELL",
		"JEROME",
		"FLOYD",
		"LEO",
		"ALVIN",
		"TIM",
		"WESLEY",
		"GORDON",
		"DEAN",
		"GREG",
		"JORGE",
		"DUSTIN",
		"PEDRO",
		"DERRICK",
		"DAN",
		"LEWIS",
		"ZACHARY",
		"COREY",
		"HERMAN",
		"MAURICE",
		"VERNON",
		"ROBERTO",
		"CLYDE",
		"GLEN",
		"HECTOR",
		"SHANE",
		"RICARDO",
		"SAM",
		"RICK",
		"LESTER",
		"BRENT",
		"RAMON",
		"CHARLIE",
		"TYLER",
		"GILBERT",
		"GENE",
		"MARC",
		"REGINALD",
		"RUBEN",
		"BRETT",
		"ANGEL",
		"NATHANIEL",
		"RAFAEL",
		"LESLIE",
		"EDGAR",
		"MILTON",
		"RAUL",
		"BEN",
		"CHESTER",
		"CECIL",
		"DUANE",
		"FRANKLIN",
		"ANDRE",
		"ELMER",
		"BRAD",
		"GABRIEL",
		"RON",
		"MITCHELL",
		"ROLAND",
		"ARNOLD",
		"HARVEY",
		"JARED",
		"ADRIAN",
		"KARL",
		"CORY",
		"CLAUDE",
		"ERIK",
		"DARRYL",
		"JAMIE",
		"NEIL",
		"JESSIE",
		"CHRISTIAN",
		"JAVIER",
		"FERNANDO",
		"CLINTON",
		"TED",
		"MATHEW",
		"TYRONE",
		"DARREN",
		"LONNIE",
		"LANCE",
		"CODY",
		"JULIO",
		"KELLY",
		"KURT",
		"ALLAN",
		"NELSON",
		"GUY",
		"CLAYTON",
		"HUGH",
		"MAX",
		"DWAYNE",
		"DWIGHT",
		"ARMANDO",
		"FELIX",
		"JIMMIE",
		"EVERETT",
		"JORDAN",
		"IAN",
		"WALLACE",
		"KEN",
		"BOB",
		"JAIME",
		"CASEY",
		"ALFREDO",
		"ALBERTO",
		"DAVE",
		"IVAN",
		"JOHNNIE",
		"SIDNEY",
		"BYRON",
		"JULIAN",
		"ISAAC",
		"MORRIS",
		"CLIFTON",
		"WILLARD",
		"DARYL",
		"ROSS",
		"VIRGIL",
		"ANDY",
		"MARSHALL",
		"SALVADOR",
		"PERRY",
		"KIRK",
		"SERGIO",
		"MARION",
		"TRACY",
		"SETH",
		"KENT",
		"TERRANCE",
		"RENE",
		"EDUARDO",
		"TERRENCE",
		"ENRIQUE",
		"FREDDIE",
		"WADE"		
	};
	
	public static final String[] LAST_NAMES = {
		"SMITH",
		"JOHNSON",
		"WILLIAMS",
		"JONES",
		"BROWN",
		"DAVIS",
		"MILLER",
		"WILSON",
		"MOORE",
		"TAYLOR",
		"ANDERSON",
		"THOMAS",
		"JACKSON",
		"WHITE",
		"HARRIS",
		"MARTIN",
		"THOMPSON",
		"GARCIA",
		"MARTINEZ",
		"ROBINSON",
		"CLARK",
		"RODRIGUEZ",
		"LEWIS",
		"LEE",
		"WALKER",
		"HALL",
		"ALLEN",
		"YOUNG",
		"HERNANDEZ",
		"KING",
		"WRIGHT",
		"LOPEZ",
		"HILL",
		"SCOTT",
		"GREEN",
		"ADAMS",
		"BAKER",
		"GONZALEZ",
		"NELSON",
		"CARTER",
		"MITCHELL",
		"PEREZ",
		"ROBERTS",
		"TURNER",
		"PHILLIPS",
		"CAMPBELL",
		"PARKER",
		"EVANS",
		"EDWARDS",
		"COLLINS",
		"STEWART",
		"SANCHEZ",
		"MORRIS",
		"ROGERS",
		"REED",
		"COOK",
		"MORGAN",
		"BELL",
		"MURPHY",
		"BAILEY",
		"RIVERA",
		"COOPER",
		"RICHARDSON",
		"COX",
		"HOWARD",
		"WARD",
		"TORRES",
		"PETERSON",
		"GRAY",
		"RAMIREZ",
		"JAMES",
		"WATSON",
		"BROOKS",
		"KELLY",
		"SANDERS",
		"PRICE",
		"BENNETT",
		"WOOD",
		"BARNES",
		"ROSS",
		"HENDERSON",
		"COLEMAN",
		"JENKINS",
		"PERRY",
		"POWELL",
		"LONG",
		"PATTERSON",
		"HUGHES",
		"FLORES",
		"WASHINGTON",
		"BUTLER",
		"SIMMONS",
		"FOSTER",
		"GONZALES",
		"BRYANT",
		"ALEXANDER",
		"RUSSELL",
		"GRIFFIN",
		"DIAZ",
		"HAYES",
		"MYERS",
		"FORD",
		"HAMILTON",
		"GRAHAM",
		"SULLIVAN",
		"WALLACE",
		"WOODS",
		"COLE",
		"WEST",
		"JORDAN",
		"OWENS",
		"REYNOLDS",
		"FISHER",
		"ELLIS",
		"HARRISON",
		"GIBSON",
		"MCDONALD",
		"CRUZ",
		"MARSHALL",
		"ORTIZ",
		"GOMEZ",
		"MURRAY",
		"FREEMAN",
		"WELLS",
		"WEBB",
		"SIMPSON",
		"STEVENS",
		"TUCKER",
		"PORTER",
		"HUNTER",
		"HICKS",
		"CRAWFORD",
		"HENRY",
		"BOYD",
		"MASON",
		"MORALES",
		"KENNEDY",
		"WARREN",
		"DIXON",
		"RAMOS",
		"REYES",
		"BURNS",
		"GORDON",
		"SHAW",
		"HOLMES",
		"RICE",
		"ROBERTSON",
		"HUNT",
		"BLACK",
		"DANIELS",
		"PALMER",
		"MILLS",
		"NICHOLS",
		"GRANT",
		"KNIGHT",
		"FERGUSON",
		"ROSE",
		"STONE",
		"HAWKINS",
		"DUNN",
		"PERKINS",
		"HUDSON",
		"SPENCER",
		"GARDNER",
		"STEPHENS",
		"PAYNE",
		"PIERCE",
		"BERRY",
		"MATTHEWS",
		"ARNOLD",
		"WAGNER",
		"WILLIS",
		"RAY",
		"WATKINS",
		"OLSON",
		"CARROLL",
		"DUNCAN",
		"SNYDER",
		"HART",
		"CUNNINGHAM",
		"BRADLEY",
		"LANE",
		"ANDREWS",
		"RUIZ",
		"HARPER",
		"FOX",
		"RILEY",
		"ARMSTRONG",
		"CARPENTER",
		"WEAVER",
		"GREENE",
		"LAWRENCE",
		"ELLIOTT",
		"CHAVEZ",
		"SIMS",
		"AUSTIN",
		"PETERS",
		"KELLEY",
		"FRANKLIN",
		"LAWSON",
		"FIELDS",
		"GUTIERREZ",
		"RYAN",
		"SCHMIDT",
		"CARR",
		"VASQUEZ",
		"CASTILLO",
		"WHEELER",
		"CHAPMAN",
		"OLIVER",
		"MONTGOMERY",
		"RICHARDS",
		"WILLIAMSON",
		"JOHNSTON",
		"BANKS",
		"MEYER",
		"BISHOP",
		"MCCOY",
		"HOWELL",
		"ALVAREZ",
		"MORRISON",
		"HANSEN",
		"FERNANDEZ",
		"GARZA",
		"HARVEY",
		"LITTLE",
		"BURTON",
		"STANLEY",
		"NGUYEN",
		"GEORGE",
		"JACOBS",
		"REID",
		"KIM",
		"FULLER",
		"LYNCH",
		"DEAN",
		"GILBERT",
		"GARRETT",
		"ROMERO",
		"WELCH",
		"LARSON",
		"FRAZIER",
		"BURKE",
		"HANSON",
		"DAY",
		"MENDOZA",
		"MORENO",
		"BOWMAN",
		"MEDINA",
		"FOWLER",
		"BREWER",
		"HOFFMAN",
		"CARLSON",
		"SILVA",
		"PEARSON",
		"HOLLAND",
		"DOUGLAS",
		"FLEMING",
		"JENSEN",
		"VARGAS",
		"BYRD",
		"DAVIDSON",
		"HOPKINS",
		"MAY",
		"TERRY",
		"HERRERA",
		"WADE",
		"SOTO",
		"WALTERS",
		"CURTIS",
		"NEAL",
		"CALDWELL",
		"LOWE",
		"JENNINGS",
		"BARNETT",
		"GRAVES",
		"JIMENEZ",
		"HORTON",
		"SHELTON",
		"BARRETT",
		"OBRIEN",
		"CASTRO",
		"SUTTON",
		"GREGORY",
		"MCKINNEY",
		"LUCAS",
		"MILES",
		"CRAIG",
		"RODRIQUEZ",
		"CHAMBERS",
		"HOLT",
		"LAMBERT",
		"FLETCHER",
		"WATTS",
		"BATES",
		"HALE",
		"RHODES",
		"PENA",
		"BECK",
		"NEWMAN",
		"HAYNES",
		"MCDANIEL",
		"MENDEZ",
		"BUSH",
		"VAUGHN",
		"PARKS",
		"DAWSON",
		"SANTIAGO",
		"NORRIS",
		"HARDY",
		"LOVE",
		"STEELE",
		"CURRY",
		"POWERS",
		"SCHULTZ",
		"BARKER",
		"GUZMAN",
		"PAGE",
		"MUNOZ",
		"BALL",
		"KELLER",
		"CHANDLER",
		"WEBER",
		"LEONARD",
		"WALSH",
		"LYONS",
		"RAMSEY",
		"WOLFE",
		"SCHNEIDER",
		"MULLINS",
		"BENSON",
		"SHARP",
		"BOWEN",
		"DANIEL",
		"BARBER",
		"CUMMINGS",
		"HINES",
		"BALDWIN",
		"GRIFFITH",
		"VALDEZ",
		"HUBBARD",
		"SALAZAR",
		"REEVES",
		"WARNER",
		"STEVENSON",
		"BURGESS",
		"SANTOS",
		"TATE",
		"CROSS",
		"GARNER",
		"MANN",
		"MACK",
		"MOSS",
		"THORNTON",
		"DENNIS",
		"MCGEE",
		"FARMER",
		"DELGADO",
		"AGUILAR",
		"VEGA",
		"GLOVER",
		"MANNING",
		"COHEN",
		"HARMON",
		"RODGERS",
		"ROBBINS",
		"NEWTON",
		"TODD",
		"BLAIR",
		"HIGGINS",
		"INGRAM",
		"REESE",
		"CANNON",
		"STRICKLAND",
		"TOWNSEND",
		"POTTER",
		"GOODWIN",
		"WALTON",
		"ROWE",
		"HAMPTON",
		"ORTEGA",
		"PATTON",
		"SWANSON",
		"JOSEPH",
		"FRANCIS",
		"GOODMAN",
		"MALDONADO",
		"YATES",
		"BECKER",
		"ERICKSON",
		"HODGES",
		"RIOS",
		"CONNER",
		"ADKINS",
		"WEBSTER",
		"NORMAN",
		"MALONE",
		"HAMMOND",
		"FLOWERS",
		"COBB",
		"MOODY",
		"QUINN",
		"BLAKE",
		"MAXWELL",
		"POPE",
		"FLOYD",
		"OSBORNE",
		"PAUL",
		"MCCARTHY",
		"GUERRERO",
		"LINDSEY",
		"ESTRADA",
		"SANDOVAL",
		"GIBBS",
		"TYLER",
		"GROSS",
		"FITZGERALD",
		"STOKES",
		"DOYLE",
		"SHERMAN",
		"SAUNDERS",
		"WISE",
		"COLON",
		"GILL",
		"ALVARADO",
		"GREER",
		"PADILLA",
		"SIMON",
		"WATERS",
		"NUNEZ",
		"BALLARD",
		"SCHWARTZ",
		"MCBRIDE",
		"HOUSTON",
		"CHRISTENSEN",
		"KLEIN",
		"PRATT",
		"BRIGGS",
		"PARSONS",
		"MCLAUGHLIN",
		"ZIMMERMAN",
		"FRENCH",
		"BUCHANAN",
		"MORAN",
		"COPELAND",
		"ROY",
		"PITTMAN",
		"BRADY",
		"MCCORMICK",
		"HOLLOWAY",
		"BROCK",
		"POOLE",
		"FRANK",
		"LOGAN",
		"OWEN",
		"BASS",
		"MARSH",
		"DRAKE",
		"WONG",
		"JEFFERSON",
		"PARK",
		"MORTON",
		"ABBOTT",
		"SPARKS",
		"PATRICK",
		"NORTON",
		"HUFF",
		"CLAYTON",
		"MASSEY",
		"LLOYD",
		"FIGUEROA",
		"CARSON",
		"BOWERS",
		"ROBERSON",
		"BARTON",
		"TRAN",
		"LAMB",
		"HARRINGTON",
		"CASEY",
		"BOONE",
		"CORTEZ",
		"CLARKE",
		"MATHIS",
		"SINGLETON",
		"WILKINS",
		"CAIN",
		"BRYAN",
		"UNDERWOOD",
		"HOGAN",
		"MCKENZIE",
		"COLLIER",
		"LUNA",
		"PHELPS",
		"MCGUIRE",
		"ALLISON",
		"BRIDGES",
		"WILKERSON",
		"NASH",
		"SUMMERS",
		"ATKINS",
		"WILCOX",
		"PITTS",
		"CONLEY",
		"MARQUEZ",
		"BURNETT",
		"RICHARD",
		"COCHRAN",
		"CHASE",
		"DAVENPORT",
		"HOOD",
		"GATES",
		"CLAY",
		"AYALA",
		"SAWYER",
		"ROMAN",
		"VAZQUEZ",
		"DICKERSON",
		"HODGE",
		"ACOSTA",
		"FLYNN",
		"ESPINOZA",
		"NICHOLSON",
		"MONROE",
		"WOLF",
		"MORROW",
		"KIRK",
		"RANDALL",
		"ANTHONY",
		"WHITAKER",
		"OCONNOR",
		"SKINNER",
		"WARE",
		"MOLINA",
		"KIRBY",
		"HUFFMAN",
		"BRADFORD",
		"CHARLES",
		"GILMORE",
		"DOMINGUEZ",
		"ONEAL",
		"BRUCE",
		"LANG",
		"COMBS",
		"KRAMER",
		"HEATH",
		"HANCOCK",
		"GALLAGHER",
		"GAINES",
		"SHAFFER",
		"SHORT",
		"WIGGINS",
		"MATHEWS",
		"MCCLAIN",
		"FISCHER",
		"WALL",
		"SMALL",
		"MELTON",
		"HENSLEY",
		"BOND",
		"DYER",
		"CAMERON",
		"GRIMES",
		"CONTRERAS",
		"CHRISTIAN",
		"WYATT",
		"BAXTER",
		"SNOW",
		"MOSLEY",
		"SHEPHERD",
		"LARSEN",
		"HOOVER",
		"BEASLEY",
		"GLENN",
		"PETERSEN",
		"WHITEHEAD",
		"MEYERS",
		"KEITH",
		"GARRISON",
		"VINCENT",
		"SHIELDS",
		"HORN",
		"SAVAGE",
		"OLSEN",
		"SCHROEDER",
		"HARTMAN",
		"WOODARD",
		"MUELLER",
		"KEMP",
		"DELEON",
		"BOOTH",
		"PATEL",
		"CALHOUN",
		"WILEY",
		"EATON",
		"CLINE",
		"NAVARRO",
		"HARRELL",
		"LESTER",
		"HUMPHREY",
		"PARRISH",
		"DURAN",
		"HUTCHINSON",
		"HESS",
		"DORSEY",
		"BULLOCK",
		"ROBLES",
		"BEARD",
		"DALTON",
		"AVILA",
		"VANCE",
		"RICH",
		"BLACKWELL",
		"YORK",
		"JOHNS",
		"BLANKENSHIP",
		"TREVINO",
		"SALINAS",
		"CAMPOS",
		"PRUITT",
		"MOSES",
		"CALLAHAN",
		"GOLDEN",
		"MONTOYA",
		"HARDIN",
		"GUERRA",
		"MCDOWELL",
		"CAREY",
		"STAFFORD",
		"GALLEGOS",
		"HENSON",
		"WILKINSON",
		"BOOKER",
		"MERRITT",
		"MIRANDA",
		"ATKINSON",
		"ORR",
		"DECKER",
		"HOBBS",
		"PRESTON",
		"TANNER",
		"KNOX",
		"PACHECO",
		"STEPHENSON",
		"GLASS",
		"ROJAS",
		"SERRANO",
		"MARKS",
		"HICKMAN",
		"ENGLISH",
		"SWEENEY",
		"STRONG",
		"PRINCE",
		"MCCLURE",
		"CONWAY",
		"WALTER",
		"ROTH",
		"MAYNARD",
		"FARRELL",
		"LOWERY",
		"HURST",
		"NIXON",
		"WEISS",
		"TRUJILLO",
		"ELLISON",
		"SLOAN",
		"JUAREZ",
		"WINTERS",
		"MCLEAN",
		"RANDOLPH",
		"LEON",
		"BOYER",
		"VILLARREAL",
		"MCCALL",
		"GENTRY",
		"CARRILLO",
		"KENT",
		"AYERS",
		"LARA",
		"SHANNON",
		"SEXTON",
		"PACE",
		"HULL",
		"LEBLANC",
		"BROWNING",
		"VELASQUEZ",
		"LEACH",
		"CHANG",
		"HOUSE",
		"SELLERS",
		"HERRING",
		"NOBLE",
		"FOLEY",
		"BARTLETT",
		"MERCADO",
		"LANDRY",
		"DURHAM",
		"WALLS",
		"BARR",
		"MCKEE",
		"BAUER",
		"RIVERS",
		"EVERETT",
		"BRADSHAW",
		"PUGH",
		"VELEZ",
		"RUSH",
		"ESTES",
		"DODSON",
		"MORSE",
		"SHEPPARD",
		"WEEKS",
		"CAMACHO",
		"BEAN",
		"BARRON",
		"LIVINGSTON",
		"MIDDLETON",
		"SPEARS",
		"BRANCH",
		"BLEVINS",
		"CHEN",
		"KERR",
		"MCCONNELL",
		"HATFIELD",
		"HARDING",
		"ASHLEY",
		"SOLIS",
		"HERMAN",
		"FROST",
		"GILES",
		"BLACKBURN",
		"WILLIAM",
		"PENNINGTON",
		"WOODWARD",
		"FINLEY",
		"MCINTOSH",
		"KOCH",
		"BEST",
		"SOLOMON",
		"MCCULLOUGH",
		"DUDLEY",
		"NOLAN",
		"BLANCHARD",
		"RIVAS",
		"BRENNAN",
		"MEJIA",
		"KANE",
		"BENTON",
		"JOYCE",
		"BUCKLEY",
		"HALEY",
		"VALENTINE",
		"MADDOX",
		"RUSSO",
		"MCKNIGHT",
		"BUCK",
		"MOON",
		"MCMILLAN",
		"CROSBY",
		"BERG",
		"DOTSON",
		"MAYS",
		"ROACH",
		"CHURCH",
		"CHAN",
		"RICHMOND",
		"MEADOWS",
		"FAULKNER",
		"ONEILL",
		"KNAPP",
		"KLINE",
		"BARRY",
		"OCHOA",
		"JACOBSON",
		"GAY",
		"AVERY",
		"HENDRICKS",
		"HORNE",
		"SHEPARD",
		"HEBERT",
		"CHERRY",
		"CARDENAS",
		"MCINTYRE",
		"WHITNEY",
		"WALLER",
		"HOLMAN",
		"DONALDSON",
		"CANTU",
		"TERRELL",
		"MORIN",
		"GILLESPIE",
		"FUENTES",
		"TILLMAN",
		"SANFORD",
		"BENTLEY",
		"PECK",
		"KEY",
		"SALAS",
		"ROLLINS",
		"GAMBLE",
		"DICKSON",
		"BATTLE",
		"SANTANA",
		"CABRERA",
		"CERVANTES",
		"HOWE",
		"HINTON",
		"HURLEY",
		"SPENCE",
		"ZAMORA",
		"YANG",
		"MCNEIL",
		"SUAREZ",
		"CASE",
		"PETTY",
		"GOULD",
		"MCFARLAND",
		"SAMPSON",
		"CARVER",
		"BRAY",
		"ROSARIO",
		"MACDONALD",
		"STOUT",
		"HESTER",
		"MELENDEZ",
		"DILLON",
		"FARLEY",
		"HOPPER",
		"GALLOWAY",
		"POTTS",
		"BERNARD",
		"JOYNER",
		"STEIN",
		"AGUIRRE",
		"OSBORN",
		"MERCER",
		"BENDER",
		"FRANCO",
		"ROWLAND",
		"SYKES",
		"BENJAMIN",
		"TRAVIS",
		"PICKETT",
		"CRANE",
		"SEARS",
		"MAYO",
		"DUNLAP",
		"HAYDEN",
		"WILDER",
		"MCKAY",
		"COFFEY",
		"MCCARTY",
		"EWING",
		"COOLEY",
		"VAUGHAN",
		"BONNER",
		"COTTON",
		"HOLDER",
		"STARK",
		"FERRELL",
		"CANTRELL",
		"FULTON",
		"LYNN",
		"LOTT",
		"CALDERON",
		"ROSA",
		"POLLARD",
		"HOOPER",
		"BURCH",
		"MULLEN",
		"FRY",
		"RIDDLE",
		"LEVY",
		"DAVID",
		"DUKE",
		"ODONNELL",
		"GUY",
		"MICHAEL",
		"BRITT",
		"FREDERICK",
		"DAUGHERTY",
		"BERGER",
		"DILLARD",
		"ALSTON",
		"JARVIS",
		"FRYE",
		"RIGGS",
		"CHANEY",
		"ODOM",
		"DUFFY",
		"FITZPATRICK",
		"VALENZUELA",
		"MERRILL",
		"MAYER",
		"ALFORD",
		"MCPHERSON",
		"ACEVEDO",
		"DONOVAN",
		"BARRERA",
		"ALBERT",
		"COTE",
		"REILLY",
		"COMPTON",
		"RAYMOND",
		"MOONEY",
		"MCGOWAN",
		"CRAFT",
		"CLEVELAND",
		"CLEMONS",
		"WYNN",
		"NIELSEN",
		"BAIRD",
		"STANTON",
		"SNIDER",
		"ROSALES",
		"BRIGHT",
		"WITT",
		"STUART",
		"HAYS",
		"HOLDEN",
		"RUTLEDGE",
		"KINNEY",
		"CLEMENTS",
		"CASTANEDA",
		"SLATER",
		"HAHN",
		"EMERSON",
		"CONRAD",
		"BURKS",
		"DELANEY",
		"PATE",
		"LANCASTER",
		"SWEET",
		"JUSTICE",
		"TYSON",
		"SHARPE",
		"WHITFIELD",
		"TALLEY",
		"MACIAS",
		"IRWIN",
		"BURRIS",
		"RATLIFF",
		"MCCRAY",
		"MADDEN",
		"KAUFMAN",
		"BEACH",
		"GOFF",
		"CASH",
		"BOLTON",
		"MCFADDEN",
		"LEVINE",
		"GOOD",
		"BYERS",
		"KIRKLAND",
		"KIDD",
		"WORKMAN",
		"CARNEY",
		"DALE",
		"MCLEOD",
		"HOLCOMB",
		"ENGLAND",
		"FINCH",
		"HEAD",
		"BURT",
		"HENDRIX",
		"SOSA",
		"HANEY",
		"FRANKS",
		"SARGENT",
		"NIEVES",
		"DOWNS",
		"RASMUSSEN",
		"BIRD",
		"HEWITT",
		"LINDSAY",
		"LE",
		"FOREMAN",
		"VALENCIA",
		"ONEIL",
		"DELACRUZ",
		"VINSON",
		"DEJESUS",
		"HYDE",
		"FORBES",
		"GILLIAM",
		"GUTHRIE",
		"WOOTEN",
		"HUBER",
		"BARLOW",
		"BOYLE",
		"MCMAHON",
		"BUCKNER",
		"ROCHA",
		"PUCKETT",
		"LANGLEY",
		"KNOWLES",
		"COOKE",
		"VELAZQUEZ",
		"WHITLEY",
		"NOEL",
		"VANG"
	};
}
