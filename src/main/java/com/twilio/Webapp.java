package com.twilio;

import com.twilio.jwt.accesstoken.AccessToken;
import com.twilio.jwt.accesstoken.VoiceGrant;
import com.twilio.type.*;
import com.twilio.twiml.voice.Client;
import com.twilio.twiml.voice.Dial;
import com.twilio.twiml.voice.Number;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Say;
import com.twilio.twiml.TwiMLException;
import com.twilio.http.TwilioRestClient;
import com.twilio.http.HttpMethod;
import com.twilio.rest.api.v2010.account.Call;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import java.net.URI;

import static spark.Spark.afterAfter;
import static spark.Spark.get;
import static spark.Spark.post;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.Character;


public class Webapp {

    static final String IDENTITY = "alice";
    static final String CALLER_ID = "client:quick_start";
    // Use a valid Twilio number by adding to your account via https://www.twilio.com/console/phone-numbers/verified
    static final String CALLER_NUMBER = "1234567890";

    public static void main(String[] args) throws Exception {
        // Load the .env file into environment
        dotenv();

        // Log all requests and responses
        afterAfter(new LoggingFilter());

        get("/", (request, response) -> {
            return welcome();
        });

        post("/", (request, response) -> {
           return welcome();
        });

        /**
         * Creates an access token with VoiceGrant using your Twilio credentials.
         *
         * @returns The Access Token string
         */
        get("/accessToken", (request, response) -> {
            // Read the identity param provided
            final String identity = request.queryParams("identity") != null ? request.queryParams("identity") : IDENTITY;
            return getAccessToken(identity);
        });

        /**
         * Creates an access token with VoiceGrant using your Twilio credentials.
         *
         * @returns The Access Token string
         */
        post("/accessToken", (request, response) -> {
            // Read the identity param provided
            String identity = null;
            List<NameValuePair> pairs = URLEncodedUtils.parse(request.body(), Charset.defaultCharset());
            Map<String, String> params = toMap(pairs);
            try {
                identity = params.get("identity");
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
            return getAccessToken(identity != null ? identity : IDENTITY);
        });

        /**
         * Creates an endpoint that can be used in your TwiML App as the Voice Request Url.
         * <br><br>
         * In order to make an outgoing call using Twilio Voice SDK, you need to provide a
         * TwiML App SID in the Access Token. You can run your server, make it publicly
         * accessible and use `/makeCall` endpoint as the Voice Request Url in your TwiML App.
         * <br><br>
         *
         * @returns The TwiMl used to respond to an outgoing call
         */
        get("/makeCall", (request, response) -> {
            final String to = request.queryParams("to");
            System.out.println(to);
            return call(to);
        });

        /**
         * Creates an endpoint that can be used in your TwiML App as the Voice Request Url.
         * <br><br>
         * In order to make an outgoing call using Twilio Voice SDK, you need to provide a
         * TwiML App SID in the Access Token. You can run your server, make it publicly
         * accessible and use `/makeCall` endpoint as the Voice Request Url in your TwiML App.
         *
         * <br><br>
         *
         * @returns The TwiMl used to respond to an outgoing call
         */
        post("/makeCall", (request, response) -> {
            String to = "";
            List<NameValuePair> pairs = URLEncodedUtils.parse(request.body(), Charset.defaultCharset());
            Map<String, String> params = toMap(pairs);
            try {
                to = params.get("to");
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
            System.out.println(to);
            return call(to);
        });

        /**
         * Makes a call to the specified client using the Twilio REST API.
         *
         * @returns The CallSid
         */
        get("/placeCall", (request, response) -> {
            final String to = request.queryParams("to");
            // The fully qualified URL that should be consulted by Twilio when the call connects.
            URI uri = URI.create(request.scheme() + "://" + request.host() + "/incoming");
            System.out.println(uri.toURL().toString());
            return callUsingRestClient(to, uri);
        });

        /**
         * Makes a call to the specified client using the Twilio REST API.
         *
         * @returns The CallSid
         */
        post("/placeCall", (request, response) -> {
            String to = "";
            List<NameValuePair> pairs = URLEncodedUtils.parse(request.body(), Charset.defaultCharset());
            Map<String, String> params = toMap(pairs);
            try {
                to = params.get("to");
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
            // The fully qualified URL that should be consulted by Twilio when the call connects.
            URI uri = URI.create(request.scheme() + "://" + request.host() + "/incoming");
            System.out.println(uri.toURL().toString());
            return callUsingRestClient(to, uri);
        });

        /**
         * Creates an endpoint that plays back a greeting.
         */
        get("/incoming", (request, response) -> {
            return greet();
        });

        /**
         * Creates an endpoint that plays back a greeting.
         */
        post("/incoming", (request, response) -> {
            return greet();
        });
    }

    private static String getAccessToken(final String identity) {
        // Create Voice grant
        VoiceGrant grant = new VoiceGrant();
        grant.setOutgoingApplicationSid(System.getProperty("APP_SID"));
        grant.setPushCredentialSid(System.getProperty("PUSH_CREDENTIAL_SID"));

        // Create access token
        AccessToken token = new AccessToken.Builder(
                System.getProperty("ACCOUNT_SID"),
                System.getProperty("API_KEY"),
                System.getProperty("API_SECRET")
        ).identity(identity).grant(grant).build();
        System.out.println(token.toJwt());
        return token.toJwt();
    }

    private static String call(final String to) {
        VoiceResponse voiceResponse;
        String toXml = null;
        if (to == null || to.isEmpty()) {
            Say say = new Say.Builder("Congratulations! You have made your first call! Good bye.").build();
            voiceResponse = new VoiceResponse.Builder().say(say).build();
        } else if (isPhoneNumber(to)) {
            Number number = new Number.Builder(to).build();
            Dial dial = new Dial.Builder().callerId(CALLER_NUMBER).number(number)
                    .build();
            voiceResponse = new VoiceResponse.Builder().dial(dial).build();
        } else {
            Client client = new Client.Builder(to).build();
            Dial dial = new Dial.Builder().callerId(CALLER_ID).client(client)
                    .build();
            voiceResponse = new VoiceResponse.Builder().dial(dial).build();
        }
        try {
            toXml = voiceResponse.toXml();
        } catch (TwiMLException e) {
            e.printStackTrace();
        }
        return toXml;
    }

    private static String callUsingRestClient(final String to, final URI uri) {
        final TwilioRestClient client = new TwilioRestClient.Builder(System.getProperty("API_KEY"), System.getProperty("API_SECRET"))
                .accountSid(System.getProperty("ACCOUNT_SID"))
                .build();

        if (to == null || to.isEmpty()) {
            com.twilio.type.Client clientEndpoint = new com.twilio.type.Client("client:" + IDENTITY);
            PhoneNumber from = new PhoneNumber(CALLER_ID);
            // Make the call
            Call call = Call.creator(clientEndpoint, from, uri).setMethod(HttpMethod.GET).create(client);
            // Print the call SID (a 32 digit hex like CA123..)
            System.out.println(call.getSid());
            return call.getSid();
        } else if (isNumeric(to)) {
            com.twilio.type.Client clientEndpoint = new com.twilio.type.Client(to);
            PhoneNumber from = new PhoneNumber(CALLER_NUMBER);
            // Make the call
            Call call = Call.creator(clientEndpoint, from, uri).setMethod(HttpMethod.GET).create(client);
            // Print the call SID (a 32 digit hex like CA123..)
            System.out.println(call.getSid());
            return call.getSid();
        } else {
            com.twilio.type.Client clientEndpoint = new com.twilio.type.Client("client:" + to);
            PhoneNumber from = new PhoneNumber(CALLER_ID);
            // Make the call
            Call call = Call.creator(clientEndpoint, from, uri).setMethod(HttpMethod.GET).create(client);
            // Print the call SID (a 32 digit hex like CA123..)
            System.out.println(call.getSid());
            return call.getSid();
        }
    }

    private static String greet() {
        VoiceResponse voiceResponse;
        Say say = new Say.Builder("Congratulations! You have received your first inbound call! Good bye.").build();
        voiceResponse = new VoiceResponse.Builder().say(say).build();
        System.out.println(voiceResponse.toXml().toString());
        return voiceResponse.toXml();
    }

    private static String welcome() {
        VoiceResponse voiceResponse;
        Say say = new Say.Builder("Welcome to Twilio").build();
        voiceResponse = new VoiceResponse.Builder().say(say).build();
        System.out.println(voiceResponse.toXml().toString());
        return voiceResponse.toXml();
    }

    private static void dotenv() throws Exception {
        final File env = new File(".env");
        if (!env.exists()) {
            return;
        }

        final Properties props = new Properties();
        props.load(new FileInputStream(env));
        props.putAll(System.getenv());
        props.entrySet().forEach(p -> System.setProperty(p.getKey().toString(), p.getValue().toString()));
    }

    private static Map<String, String> toMap(final List<NameValuePair> pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.size(); i++) {
            NameValuePair pair = pairs.get(i);
            System.out.println("NameValuePair - name=" + pair.getName() + " value=" + pair.getValue());
            map.put(pair.getName(), pair.getValue());
        }
        return map;
    }

    private static boolean isPhoneNumber(String s) {
        if (s.length() == 1) {
            return isNumeric(s);
        } else if (s.charAt(0) == '+') {
            return isNumeric(s.substring(1));
        } else {
            return isNumeric(s);
        }
    }

    private static boolean isNumeric(String s) {
        int len = s.length();
        for (int i = 0; i < len; ++i) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
