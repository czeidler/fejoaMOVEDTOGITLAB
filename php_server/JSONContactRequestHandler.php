<?php

include_once 'Contact.php';
include_once 'JSONProtocol.php';


class JSONContactRequestHandler extends JSONHandler {
	static private $kContactRequestStanza = "contactRequest";
	static private $kPublicKeyStanza = "publicKey";
	
	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], JSONContactRequestHandler::$kContactRequestStanza) != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;
		if (!isset($params['serverUser']) || !isset($params['uid']) || !isset($params['keyId'])
			|| !isset($params['address']) || !isset($params[JSONContactRequestHandler::$kPublicKeyStanza]))
			return false;

		$serverUser = $params['serverUser'];
		$userIdentity = Session::get()->getMainUserIdentity($serverUser);
		if ($userIdentity === null)
			return $this->makeError($jsonId, "can't find server user: ".$serverUser);

		$contact = $userIdentity->createContact($params['uid']);
		$contact->addKeySet($params['keyId'], "", $params[JSONContactRequestHandler::$kPublicKeyStanza]);
		$contact->setMainKeyId($params['keyId']);
		$contact->setAddress($params['address']);

		$userIdentity->commit();

		// reply
		$myself = $userIdentity->getMyself();
		$profile = Session::get()->getProfile($serverUser);
		$keyStore = $profile->getUserIdentityKeyStore($userIdentity);
		if ($keyStore === null)
			return $this->makeError($jsonId, "internal error");

		$mainKeyId = $myself->getMainKeyId();
		$myPublicKey;
		$keyStore->readAsymmetricKey($mainKeyId, $myPublicKey);

		return $this->makeJSONRPCReturn($jsonId, array('status' => 0, 'uid' => $myself->getUid(),
			'keyId' => $mainKeyId, 'address' => $myself->getAddress(), JSONContactRequestHandler::$kPublicKeyStanza => $myPublicKey));
	}

	public function makeError($jsonId, $message) {
		return $this->makeJSONRPCReturn($jsonId, array('status' => -1, 'message' => $message));
	}
}

?> 
 
