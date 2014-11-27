<?php

include_once 'Contact.php';
include_once 'XMLProtocol.php';


class ContactMessageConst {
	static public $kContactRequestStanza = "contactRequest";
	static public $kPublicKeyStanza = "publicKey";
};


class PublicKeyStanzaHandler extends InStanzaHandler {
	private $publicKey;
	
	public function __construct() {
		InStanzaHandler::__construct(ContactMessageConst::$kPublicKeyStanza);
	}
	
	public function handleStanza($xml) {
		$this->publicKey = $xml->readString();
		if ($this->publicKey == "")
			return false;
		return true;
	}

	public function getPublicKey() {
		return $this->publicKey;
	}
};


class ContactRequestStanzaHandler extends InStanzaHandler {
	private $inStreamReader;

	private $serverUser;
	private $uid;
    private $keyId;
    private $address;

    private $publicKeyStanzaHandler;

	public function __construct($inStreamReader) {
		InStanzaHandler::__construct(ContactMessageConst::$kContactRequestStanza);

		$this->inStreamReader = $inStreamReader;

		$this->publicKeyStanzaHandler = new PublicKeyStanzaHandler();
		$this->addChild($this->publicKeyStanzaHandler);
	}
	
	public function handleStanza($xml) {
		$this->serverUser =  $xml->getAttribute("serverUser");
		$this->uid = $xml->getAttribute("uid");
		$this->keyId = $xml->getAttribute("keyId");
		$this->address = $xml->getAttribute("address");

		if ($this->serverUser == "" || $this->uid == "" || $this->keyId == "")
			return false;
		return true;
	}

	public function finished() {
		$publicKey = $this->publicKeyStanzaHandler->getPublicKey();

		$userIdentity = Session::get()->getMainUserIdentity($this->serverUser);
		if ($userIdentity === null) {
			$this->printError("error", "can't find server user: ".$this->serverUser);
			return;
		}

		$message = null;
		if ($userIdentity->findContact($this->uid) === null) {
			$contact = $userIdentity->createContact($this->uid);
			$contact->addKeySet($this->keyId, $publicKey);
			$contact->setMainKeyId($this->keyId);
			$contact->setAddress($this->address);

			$userIdentity->commit();
		} else
			$message = "I already know you";

		// reply
		$outStream = new ProtocolOutStream();
		$outStream->pushStanza(new IqOutStanza(IqType::$kResult));
		$stanza = new OutStanza(ContactMessageConst::$kContactRequestStanza);
		$stanza->addAttribute("status", "ok");
		if ($message !== null)
			$stanza->addAttribute("message", $message);

		$myself = $userIdentity->getMyself();
		$profile = Session::get()->getProfile($this->serverUser);
		$keyStore = $profile->getUserIdentityKeyStore($userIdentity);
		if ($keyStore === null) {
			$this->printError("error", "internal error");
			return;
		}

		$mainKeyId = $myself->getMainKeyId();
		$myPublicKey;
		$keyStore->readAsymmetricKey($mainKeyId, $myPublicKey);

		$stanza->addAttribute("uid", $myself->getUid());
		$stanza->addAttribute("keyId", $mainKeyId);
		$stanza->addAttribute("address", $myself->getAddress());
    
		$outStream->pushChildStanza($stanza);

		$publicKeyStanza = new OutStanza(ContactMessageConst::$kPublicKeyStanza);
		$publicKeyStanza->setText($myPublicKey);
		$outStream->pushChildStanza($publicKeyStanza);
		$outStream->cdDotDot();

		$this->inStreamReader->appendResponse($outStream->flush());
	}
	
	public function printError($error, $message) {
		// produce output
		$outStream = new ProtocolOutStream();
		$outStream->pushStanza(new IqOutStanza(IqType::$kResult));
		$stanza = new OutStanza(ContactMessageConst::$kContactRequestStanza);
		$stanza->addAttribute("status", $error);
		$stanza->setText($message);
		$outStream->pushChildStanza($stanza);
		$this->inStreamReader->appendResponse($outStream->flush());
	}
}

?>