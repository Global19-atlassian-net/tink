// Copyright 2018 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.subtle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.crypto.tink.subtle.Enums.HashType;
import com.google.crypto.tink.testing.TestUtil;
import com.google.crypto.tink.testing.WycheproofTestUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for RsaSsaPssVerifyJce. */
@RunWith(JUnit4.class)
public class RsaSsaPssVerifyJceTest {
  @Test
  public void testConstructorExceptions() throws Exception {
    int keySize = 2048;
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(keySize);
    RSAPublicKey pub = (RSAPublicKey) keyGen.generateKeyPair().getPublic();
    try {
      new RsaSsaPssVerifyJce(pub, HashType.SHA1, HashType.SHA1, 20);
      fail("Unsafe hash, should have thrown exception.");
    } catch (GeneralSecurityException e) {
      // Expected.
      TestUtil.assertExceptionContains(e, "Unsupported hash: SHA1");
    }
  }

  @Test
  public void testWycheproofVectors() throws Exception {
    testWycheproofVectors(
        "../wycheproof/testvectors/rsa_pss_2048_sha256_mgf1_0_test.json");
    testWycheproofVectors(
        "../wycheproof/testvectors/rsa_pss_2048_sha256_mgf1_32_test.json");
    testWycheproofVectors(
        "../wycheproof/testvectors/rsa_pss_3072_sha256_mgf1_32_test.json");
    testWycheproofVectors(
        "../wycheproof/testvectors/rsa_pss_4096_sha256_mgf1_32_test.json");
    testWycheproofVectors(
        "../wycheproof/testvectors/rsa_pss_4096_sha512_mgf1_32_test.json");
  }

  private static void testWycheproofVectors(String fileName) throws Exception {
    JsonObject jsonObj = WycheproofTestUtil.readJson(fileName);

    int errors = 0;
    JsonArray testGroups = jsonObj.getAsJsonArray("testGroups");
    for (int i = 0; i < testGroups.size(); i++) {
      JsonObject group = testGroups.get(i).getAsJsonObject();

      KeyFactory kf = KeyFactory.getInstance("RSA");
      byte[] encodedPubKey = Hex.decode(group.get("keyDer").getAsString());
      X509EncodedKeySpec x509keySpec = new X509EncodedKeySpec(encodedPubKey);
      HashType sigHash = WycheproofTestUtil.getHashType(group.get("sha").getAsString());
      HashType mgf1Hash = WycheproofTestUtil.getHashType(group.get("mgfSha").getAsString());
      int saltLength = group.get("sLen").getAsInt();

      JsonArray tests = group.getAsJsonArray("tests");
      for (int j = 0; j < tests.size(); j++) {
        JsonObject testcase = tests.get(j).getAsJsonObject();
        String tcId =
            String.format("testcase %d (%s)",
                testcase.get("tcId").getAsInt(), testcase.get("comment").getAsString());
        RsaSsaPssVerifyJce verifier;
        RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(x509keySpec);
        verifier = new RsaSsaPssVerifyJce(pubKey, sigHash, mgf1Hash, saltLength);
        byte[] msg = Hex.decode(testcase.get("msg").getAsString());
        byte[] sig = Hex.decode(testcase.get("sig").getAsString());
        String result = testcase.get("result").getAsString();
        try {
          verifier.verify(sig, msg);
          if (result.equals("invalid")) {
            System.out.printf("FAIL %s: accepting invalid signature%n", tcId);
            errors++;
          }
        } catch (GeneralSecurityException ex) {
          if (result.equals("valid")) {
            System.out.printf("FAIL %s: rejecting valid signature, exception: %s%n", tcId, ex);
            errors++;
          }
        }
      }
    }
    assertEquals(0, errors);
  }
}
