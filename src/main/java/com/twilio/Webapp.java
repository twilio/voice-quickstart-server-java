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

        // Create an access token which we will sign and return to the client,
        // containing the grant we just created
        get("/accessToken", (request, response) -> {
            // Read the identity param provided
            final String identity = request.queryParams("identity") != null ? request.queryParams("identity") : IDENTITY;

            // Create Voice grant
            VoiceGrant grant = new VoiceGrant();
            grant.setOutgoingApplicationSid(System.getProperty("OUTGOING_APP_SID"));
            grant.setPushCredentialSid(System.getProperty("PUSH_CREDENTIAL_SID"));

            // Create access token
            AccessToken token = new AccessToken.Builder(
                    System.getProperty("ACCOUNT_SID"),
                    System.getProperty("API_KEY"),
                    System.getProperty("API_SECRET")
            ).identity(identity).grant(grant).build();

            System.out.println(token.toJwt());

            return token.toJwt();
        });

        get("/makeCall", (request, response) -> {
            // Load the .env file into environment
            dotenv();

            // Log all requests and responses
            afterAfter(new LoggingFilter());

            final String to = request.queryParams("to");
            VoiceResponse voiceResponse;

            if (to == null) {
                Say say = new Say.Builder("Congratulations! You have made your first call! Good bye.").build();
                voiceResponse = new VoiceResponse.Builder().say(say).build();
            } else if (Character.isDigit(to.charAt(0)) || to.charAt(0) == '+') {
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
                System.out.println(voiceResponse.toXml());
            } catch (TwiMLException e) {
                e.printStackTrace();
            }

            return voiceResponse.toXml();
        });

        // Place an outgoing call using REST Api
        get("/placeCall", (request, response) -> {
            // Load the .env file into environment
            dotenv();

            // Log all requests and responses
            afterAfter(new LoggingFilter());

            final String to = request.queryParams("to");

            final TwilioRestClient client = new TwilioRestClient.Builder(System.getProperty("API_KEY"), System.getProperty("API_SECRET"))
                    .accountSid(System.getProperty("ACCOUNT_SID"))
                    .build();

            com.twilio.type.Client clientEndpoint = new com.twilio.type.Client(to);
            PhoneNumber from = new PhoneNumber(CALLER_NUMBER);
            URI uri = URI.create(request.scheme() + "://" + request.host() + "/incomingCall");

            // Make the call
            Call call = Call.creator(clientEndpoint, from, uri).setMethod(HttpMethod.GET).create(client);

            // Print the call SID (a 32 digit hex like CA123..)
            System.out.println(call.getSid());

            return call.getSid();
        });

        get("/incomingCall", (request, response) -> {
            // Load the .env file into environment
            dotenv();

            // Log all requests and responses
            afterAfter(new LoggingFilter());

            VoiceResponse voiceResponse;
            Say say = new Say.Builder("Congratulations! You have received your first inbound call! Good bye.").build();
            voiceResponse = new VoiceResponse.Builder().say(say).build();

            System.out.println(voiceResponse.toXml().toString());

            return voiceResponse.toXml();
        });
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
}
