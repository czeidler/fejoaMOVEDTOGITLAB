<?php

include_once 'Contact.php';
include_once 'JSONProtocol.php';


class JSONContactRequestHandler extends JSONHandler {
	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], "contact_request") != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;
		if (!isset($params['serverUser']) || !isset($params['uid']) || !isset($params['keyId'])
			|| !isset($params['address']) || !isset($params['public_key']))
			return false;

		$userIdentity = Session::get()->getMainUserIdentity($params['serverUser']);
		if ($userIdentity === null)
			return $this->makeError($jsonId, "can't find server user: ".$params['serverUser']);

		$contact = $userIdentity->createContact($params['uid']);
		$contact->addKeySet($params['keyId'], "", $params['public_key']);
		$contact->setMainKeyId($params['keyId']);
		$contact->setAddress($params['address']);

		$userIdentity->commit();

		// reply
		$myself = $userIdentity->getMyself();
		$profile = Session::get()->getProfile($this->serverUser);
		$keyStore = $profile->getUserIdentityKeyStore($userIdentity);
		if ($keyStore === null)
			return $this->makeError($jsonId, "internal error");

		$mainKeyId = $myself->getMainKeyId();
		$myCertificate;
		$myPublicKey;
		$keyStore->readAsymmetricKey($mainKeyId, $myCertificate, $myPublicKey);

		return $this->makeJSONRPCReturn($jsonId, array('status' => true, 'uid' => $myself->getUid(),
			'keyId' => $mainKeyId, 'address' => $myself->getAddress(), 'public_key' => $myPublicKey));
	}

	public function makeError($jsonId, $message) {
		return $this->makeJSONRPCReturn($jsonId, array('status' => false, 'message' => $message));
	}
}

?> 
 
