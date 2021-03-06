package com.hunorkovacs.koauth.service.consumer

import com.hunorkovacs.koauth.domain.OauthParams._
import com.hunorkovacs.koauth.domain.KoauthRequest
import com.hunorkovacs.koauth.service.Arithmetics.{sign, concatItemsForSignature, createAuthorizationHeader}
import com.hunorkovacs.koauth.service.DefaultTokenGenerator.generateNonce

import scala.concurrent.{ExecutionContext, Future}

trait ConsumerService {

  def createRequestTokenRequest(request: KoauthRequest,
                                consumerKey: String,
                                consumerSecret: String,
                                callback: String): Future[RequestWithInfo]

  def createAccessTokenRequest(request: KoauthRequest,
                               consumerKey: String,
                               consumerSecret: String,
                               requestToken: String,
                               requestTokenSecret: String,
                               verifier: String): Future[RequestWithInfo]

  def createOauthenticatedRequest(request: KoauthRequest,
                                  consumerKey: String,
                                  consumerSecret: String,
                                  requestToken: String,
                                  requestTokenSecret: String): Future[RequestWithInfo]

  def createGeneralSignedRequest(request: KoauthRequest): Future[RequestWithInfo]
}

case class RequestWithInfo(request: KoauthRequest, signatureBase: String, header: String)

class DefaultConsumerService(private val ec: ExecutionContext) extends ConsumerService {

  implicit private val implicitEc = ec

  override def createRequestTokenRequest(request: KoauthRequest,
                                         consumerKey: String,
                                         consumerSecret: String,
                                         callback: String): Future[RequestWithInfo] = {
    Future {
      val paramsList = createBasicParamList().::((ConsumerKeyName, consumerKey))
        .::((ConsumerSecretName, consumerSecret))
        .::((CallbackName, callback))
      KoauthRequest(request, paramsList)
    }.flatMap(createGeneralSignedRequest)
  }

  override def createAccessTokenRequest(request: KoauthRequest,
                                        consumerKey: String,
                                        consumerSecret: String,
                                        requestToken: String,
                                        requestTokenSecret: String,
                                        verifier: String): Future[RequestWithInfo] = {
    Future {
      val paramsList = createBasicParamList().::((ConsumerKeyName, consumerKey))
      .::((ConsumerSecretName, consumerSecret))
      .::((TokenName, requestToken))
      .::((TokenSecretName, requestTokenSecret))
      .::((VerifierName, verifier))
      KoauthRequest(request, paramsList)
    }.flatMap(createGeneralSignedRequest)
  }

  override def createOauthenticatedRequest(request: KoauthRequest,
                                           consumerKey: String,
                                           consumerSecret: String,
                                           requestToken: String,
                                           requestTokenSecret: String): Future[RequestWithInfo] = {
    Future {
      val paramsList = createBasicParamList().::((ConsumerKeyName, consumerKey))
        .::((ConsumerSecretName, consumerSecret))
        .::((TokenName, requestToken))
        .::((TokenSecretName, requestTokenSecret))
      KoauthRequest(request, paramsList)
    }.flatMap(createGeneralSignedRequest)
  }

  private def createBasicParamList(): List[(String, String)] = {
    List((NonceName, generateNonce),
      (VersionName, "1.0"),
      (SignatureMethodName, "HMAC-SHA1"),
      (TimestampName, (System.currentTimeMillis / 1000).toString))
  }

  def createGeneralSignedRequest(request: KoauthRequest): Future[RequestWithInfo] = {
    Future {
      val consumerSecret = request.oauthParamsMap.applyOrElse(ConsumerSecretName, (s: String) => "")
      val tokenSecret = request.oauthParamsMap.applyOrElse(TokenSecretName, (s: String) => "")
      val base = createSignatureBase(request)
      val signature = sign(base, consumerSecret, tokenSecret)
      val list = request.oauthParamsList
        .filterNot(param => ConsumerSecretName == param._1 || TokenSecretName == param._1)
        .::((SignatureName, signature))
      val header = createAuthorizationHeader(list)
      RequestWithInfo(request, base, header)
    }
  }

  def createSignatureBase(request: KoauthRequest): String = {
    val filteredList = request.oauthParamsList
      .filterNot(param => ConsumerSecretName == param._1 || TokenSecretName == param._1)
    concatItemsForSignature(KoauthRequest(request.method,
      request.urlWithoutParams,
      request.urlParams,
      request.bodyParams,
      filteredList))
  }
}
